package dev.sandoretti.ukun.api.shared.error;

import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The single {@code @RestControllerAdvice} of this service, per {@code docs/12-error-contract.md} §3.
 * It replaces the legacy {@code flash.addFlashAttribute("error", "…")} (defect #11) with a stable
 * contract: RFC 9457 {@code ProblemDetail} carrying a machine-readable {@code code} the frontend
 * switches on, and a {@code traceId} on <em>every</em> error response.
 *
 * <h2>Why it extends {@link ResponseEntityExceptionHandler}</h2>
 *
 * Because a catch-all {@code @ExceptionHandler(Exception.class)} on its own swallows the twenty-odd
 * exceptions Spring MVC already classifies. Measured, not assumed: before this, {@code GET /api/nope}
 * answered {@code 500 INTERNAL_ERROR} instead of {@code 404}, and {@code POST /api/products}
 * answered {@code 500} instead of {@code 405} — turning every route scan into a logged stack trace
 * and an alert.
 *
 * <p>The base class resolves those into the right status; {@link #createResponseEntity} is the one
 * place they all pass through, and it is where the {@code code} and the {@code traceId} are added.
 * That keeps the promise of §3 — a generic renderer plus explicit handlers, and no branch building a
 * response field by field — while making the status come out right.
 *
 * <p>Unhandled exceptions are logged <em>once</em>, here, with the full stack trace and the
 * {@code traceId} the client is about to receive ({@code docs/11-observabilidad-y-logging.md} §4).
 * That is the alternative to the "log and rethrow" pattern that prints the same trace three times.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** SQLState of a foreign key violation. {@code docs/12} §2, {@code REFERENTIAL_INTEGRITY}. */
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    private static final String ERRORS = "errors";

    /**
     * Any business failure. The advice never asks <em>which</em> exception this is: the code, the
     * status and the extensions come from the exception itself, so a new one needs no branch here.
     */
    @ExceptionHandler(UkunException.class)
    ResponseEntity<ProblemDetail> handleUkunException(UkunException exception) {
        ProblemDetail body = ProblemDetails.of(exception.getCode(), exception.getMessage());
        exception.getExtensions().forEach(body::setProperty);

        return ResponseEntity.status(body.getStatus()).body(body);
    }

    /**
     * Safety net, not the normal path. A use case that can foresee a referential conflict checks for
     * it first and throws a typed exception naming what clashes ({@code PRODUCT_IN_USE},
     * {@code DUPLICATE_VALUE}); this catches the {@code 23503} that slipped past such a check, so the
     * client still gets the contract shape instead of a raw {@code 500}.
     *
     * <p>Anything else wrapped in a {@code DataIntegrityViolationException} is a bug, not a conflict,
     * and is <strong>delegated</strong> to {@link #handleUnexpectedFailure} so it is logged as one and
     * still answers a {@code 500 INTERNAL_ERROR}.
     *
     * <p>It used to be rethrown instead, and that was wrong in a way only a unique-key violation
     * revealed: an exception thrown from inside an {@code @ExceptionHandler} is not re-resolved by the
     * advice. It propagates to the container, which forwards to {@code /error} — a path
     * {@code SecurityConfig} does not permit — so the client got a {@code 401} for what was a server
     * bug. Calling the fallback keeps the intent and drops the round trip through the container.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleIntegrityViolation(DataIntegrityViolationException exception) {
        if (!(exception.getMostSpecificCause() instanceof SQLException cause)
                || !FOREIGN_KEY_VIOLATION.equals(cause.getSQLState())) {
            return handleUnexpectedFailure(exception);
        }
        ProblemDetail body = ProblemDetails.of(ErrorCode.REFERENTIAL_INTEGRITY, "The operation violates a referential constraint");

        return ResponseEntity.status(body.getStatus()).body(body);
    }

    /**
     * Access denied <em>after</em> authentication: a {@code @PreAuthorize} that did not hold, or a
     * resource that belongs to somebody else. Without this handler the catch-all below would swallow
     * it and answer {@code 500 INTERNAL_ERROR} with a stack trace in the log — the same class of
     * defect that made this advice extend {@link ResponseEntityExceptionHandler} in the first place.
     *
     * <p>It does <strong>not</strong> emit {@code auth.authorization.denied}, in either half. The role
     * half is published by Spring Security itself and listened to in the {@code identity} module
     * ({@code docs/11-observabilidad-y-logging.md} §3); emitting it here as well would double every
     * denial in the audit. The {@code FOREIGN_RESOURCE} half — "this store is not yours" — is emitted
     * by the use case that decides it, {@code inventory}'s {@code EmployeeStoreResolver} in v0.7,
     * because by the time the exception reaches here the two values that make the event worth having
     * (the store asked for, the store the employee works in) are gone, and an {@code AccessDeniedException}
     * from a role rule is indistinguishable from one raised by a domain check.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException exception) {
        ProblemDetail body = ProblemDetails.of(ErrorCode.FORBIDDEN, "Access to this resource is not allowed");

        return ResponseEntity.status(body.getStatus()).body(body);
    }

    /**
     * An authentication that failed once the request was already inside the dispatcher. The common
     * 401 — no token at all, or one the decoder rejects — never reaches here: it is raised in the
     * filter chain and answered by the {@code AuthenticationEntryPoint} of the {@code identity}
     * module. Both paths build the body with {@link ProblemDetails}, so the client cannot tell which
     * one it hit, and should not have to.
     */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthenticationFailure(AuthenticationException exception) {
        ProblemDetail body = ProblemDetails.of(ErrorCode.UNAUTHENTICATED, "Authentication is required");

        return ResponseEntity.status(body.getStatus()).body(body);
    }

    /**
     * The fallback, for what neither the base class nor a typed exception covers. {@code detail} is
     * deliberately generic: the message of an unexpected exception can carry SQL, a table name or a
     * value the client has no business seeing ({@code docs/12} §1). What correlates the two ends is
     * the {@code traceId}, present in the log line and in the response.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpectedFailure(Exception exception) {
        ProblemDetail body = ProblemDetails.of(ErrorCode.INTERNAL_ERROR, "Unexpected error");

        log.error("Unhandled exception, traceId={}", ProblemDetails.traceIdOf(body), exception);

        return ResponseEntity.status(body.getStatus()).body(body);
    }

    /** A rejected request object — the catalog filters are bound as one. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<ValidationError> errors = exception.getBindingResult().getAllErrors().stream()
                .map(GlobalExceptionHandler::toValidationError)
                .toList();

        return handleExceptionInternal(exception, validationBody(errors), headers, status, request);
    }

    /**
     * The body is not readable JSON. Nothing was validated, because nothing could be parsed — which
     * is why the registry gives it a code of its own rather than folding it into
     * {@code VALIDATION_FAILED}: there is no field to point at.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail body = ProblemDetails.of(ErrorCode.MALFORMED_REQUEST, "The request body could not be read");

        return handleExceptionInternal(exception, body, headers, status, request);
    }

    /**
     * A path variable or a lone query parameter that could not be converted. The base class answers
     * {@code 400} already; this adds the {@code errors[]} entry naming the parameter, so the frontend
     * paints it under the right field instead of showing a bare bad request.
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        String field = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : exception.getPropertyName();

        return handleExceptionInternal(
                exception,
                validationBody(List.of(new ValidationError(field, "has the wrong type"))),
                headers,
                status,
                request);
    }

    /**
     * Constraints declared directly on handler method parameters, which Spring reports separately
     * from a binding failure since 6.1. Same {@code code} and same shape: the frontend cannot tell —
     * and should not have to tell — whether a parameter was bound into an object or taken on its own.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<ValidationError> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ValidationError(
                                result.getMethodParameter().getParameterName(), messageOf(error))))
                .toList();

        return handleExceptionInternal(exception, validationBody(errors), headers, status, request);
    }

    /**
     * The one place a {@code ProblemDetail} leaves the base class. Stamping this deep is what stops a
     * Spring MVC exception nobody anticipated — a wrong method, an unacceptable {@code Accept} — from
     * going out without a {@code code} and a {@code traceId}.
     *
     * <p>It never overwrites a {@code code} that is already there: a handler that knows <em>which</em>
     * failure this is knows better than a lookup by status. The status-based code is the default for
     * everything else, not an override.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        if (body instanceof ProblemDetail problem && !ProblemDetails.hasCode(problem)) {
            ProblemDetails.stamp(problem, ErrorCode.forStatus(statusCode));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ProblemDetail validationBody(List<ValidationError> errors) {
        ProblemDetail body = ProblemDetails.of(
                ErrorCode.VALIDATION_FAILED, "%d field(s) failed validation".formatted(errors.size()));
        body.setProperty(ERRORS, errors);

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
