package dev.sandoretti.ukun.api.shared.error;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * The single {@code @RestControllerAdvice} of this service, per {@code docs/12-error-contract.md} §3.
 * It replaces the legacy {@code flash.addFlashAttribute("error", "…")} (defect #11) with a stable
 * contract: RFC 9457 {@code ProblemDetail} carrying a machine-readable {@code code} the frontend
 * switches on.
 *
 * <p>There are exactly two paths here and no third one, which is what §3 requires: a generic renderer
 * for any {@link UkunException}, and explicit handlers for the Spring exceptions that are not one.
 * <strong>No branch builds a response field by field</strong> — every one of them goes through
 * {@link #problem}.
 *
 * <p>Unhandled exceptions are logged <em>once</em>, here, with the full stack trace and the
 * {@code traceId} that the client is about to receive ({@code docs/11-observabilidad-y-logging.md}
 * §4). That is the alternative to the "log and rethrow" pattern that prints the same trace in three
 * places.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** SQLState of a foreign key violation. {@code docs/12} §2, {@code REFERENTIAL_INTEGRITY}. */
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    /**
     * Any business failure. The advice never asks <em>which</em> exception this is: the code, the
     * status and the extensions come from the exception itself, so a new one needs no branch here.
     */
    @ExceptionHandler(UkunException.class)
    ProblemDetail handleUkunException(UkunException exception) {
        return problem(exception.getCode(), exception.getMessage(), exception.getExtensions());
    }

    /**
     * A request object that failed its constraints. {@link BindException} rather than
     * {@code MethodArgumentNotValidException} because the latter is a subclass of it: one handler
     * covers both a rejected {@code @RequestBody} and the query parameters bound as an object, which
     * is what the catalog filters are.
     *
     * <p>A value of the wrong type ({@code ?priceMin=cheap}) also lands here, as a {@code typeMismatch}
     * error, and gets a message of our own — Spring's default for those quotes the failing conversion
     * and the target class, which is operator noise the frontend would have to hide.
     */
    @ExceptionHandler(BindException.class)
    ProblemDetail handleBindingFailure(BindException exception) {
        List<ValidationError> errors = exception.getBindingResult().getAllErrors().stream()
                .map(GlobalExceptionHandler::toValidationError)
                .toList();

        return problem(
                ErrorCode.VALIDATION_FAILED,
                "%d field(s) failed validation".formatted(errors.size()),
                Map.of("errors", errors));
    }

    /**
     * Constraints declared directly on handler method parameters, which Spring reports separately
     * from a binding failure since 6.1. Same {@code code} and same shape: the frontend cannot tell —
     * and should not have to tell — whether a parameter was bound into an object or taken on its own.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleParameterValidationFailure(HandlerMethodValidationException exception) {
        List<ValidationError> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ValidationError(
                                result.getMethodParameter().getParameterName(), messageOf(error))))
                .toList();

        return problem(
                ErrorCode.VALIDATION_FAILED,
                "%d parameter(s) failed validation".formatted(errors.size()),
                Map.of("errors", errors));
    }

    /**
     * A path variable or a lone query parameter that could not be converted at all — there is no
     * binding result to read, so it is reported as the single field error it is.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        List<ValidationError> errors = List.of(new ValidationError(exception.getName(), "has the wrong type"));

        return problem(ErrorCode.VALIDATION_FAILED, "1 parameter(s) failed validation", Map.of("errors", errors));
    }

    /** The body is not readable JSON. Nothing was validated, because nothing could be parsed. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody() {
        return problem(ErrorCode.MALFORMED_REQUEST, "The request body could not be read", Map.of());
    }

    /**
     * Safety net, not the normal path. A use case that can foresee a referential conflict checks for
     * it first and throws a typed exception naming what clashes ({@code PRODUCT_IN_USE},
     * {@code DUPLICATE_VALUE}); this catches the {@code 23503} that slipped past such a check, so the
     * client still gets the contract shape instead of a raw {@code 500}.
     *
     * <p>Anything else wrapped in a {@code DataIntegrityViolationException} is a bug, not a conflict,
     * and is rethrown so it reaches {@link #handleUnexpectedFailure} and gets logged as one.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrityViolation(DataIntegrityViolationException exception) {
        if (!(exception.getMostSpecificCause() instanceof SQLException cause)
                || !FOREIGN_KEY_VIOLATION.equals(cause.getSQLState())) {
            throw exception;
        }
        return problem(ErrorCode.REFERENTIAL_INTEGRITY, "The operation violates a referential constraint", Map.of());
    }

    /**
     * The fallback. {@code detail} is deliberately generic: the message of an unexpected exception
     * can carry SQL, a table name or a value the client has no business seeing ({@code docs/12} §1).
     * What correlates the two ends is the {@code traceId}, which is in the log line and in the
     * response.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpectedFailure(Exception exception) {
        ProblemDetail body = problem(ErrorCode.INTERNAL_ERROR, "Unexpected error", Map.of());

        log.error("Unhandled exception, traceId={}", body.getProperties().get("traceId"), exception);

        return body;
    }

    /**
     * The only place a {@code ProblemDetail} is built. Every field of {@code docs/12} §1 is set here:
     * {@code type} and {@code title} derived from the code, {@code status} from the registry,
     * {@code traceId} on <em>every</em> error response, and then the code-specific extensions.
     */
    private static ProblemDetail problem(ErrorCode code, String detail, Map<String, Object> extensions) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(code.status(), detail);
        body.setType(code.type());
        body.setTitle(code.title());
        body.setProperty("code", code.name());
        body.setProperty("traceId", TraceIds.current());

        extensions.forEach(body::setProperty);

        return body;
    }

    private static ValidationError toValidationError(ObjectError error) {
        String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
        String message = "typeMismatch".equals(error.getCode()) ? "has the wrong type" : messageOf(error);

        return new ValidationError(field, message);
    }

    private static String messageOf(MessageSourceResolvable error) {
        String message = error.getDefaultMessage();
        return message == null || message.isBlank() ? "is invalid" : message;
    }
}
