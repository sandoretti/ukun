package dev.sandoretti.ukun.auth.error;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The single {@code @RestControllerAdvice} of this service, per {@code docs/12-error-contract.md} §3:
 * every error leaves as an RFC 9457 {@code ProblemDetail} carrying a machine-readable {@code code} and
 * a {@code traceId}.
 *
 * <p>It extends {@link ResponseEntityExceptionHandler} for the reason ukun-api's copy measured rather
 * than assumed: a lone {@code @ExceptionHandler(Exception.class)} swallows the twenty-odd exceptions
 * Spring MVC already classifies, turning a wrong method or an unknown path into a {@code 500} with a
 * logged stack trace. {@link #createResponseEntity} is the single point they all pass through, and
 * where the {@code code} and the {@code traceId} get stamped.
 *
 * <p><strong>Its audience is ukun-api, not a browser.</strong> These endpoints are service-to-service,
 * so the value of the shape is that the caller can branch on {@code code} — a {@code 409} that means
 * "the email is taken" has to be distinguishable from a {@code 409} that means anything else, because
 * the saga's next move differs. That is why {@code HttpAccountsAdapter} can key on one status and let
 * everything else surface.
 *
 * <p>Nothing here ever logs the request body. A {@code CreateAccountRequest} carries a plaintext
 * password, and the advice sees it in the exception of a failed validation
 * ({@code docs/11-observabilidad-y-logging.md} §4).
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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
     * The fallback. {@code detail} is deliberately generic: the message of an unexpected exception can
     * carry SQL, a column name or a value the caller has no business seeing ({@code docs/12} §1). What
     * correlates the two ends is the {@code traceId}, present in this log line and in the response.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpectedFailure(Exception exception) {
        ProblemDetail body = ProblemDetails.of(ErrorCode.INTERNAL_ERROR, "Unexpected error");

        log.error("Unhandled exception, traceId={}", ProblemDetails.traceIdOf(body), exception);

        return ResponseEntity.status(body.getStatus()).body(body);
    }

    /** A rejected request body — {@code CreateAccountRequest} is validated as one. */
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
     * The body is not readable JSON. Nothing was validated because nothing could be parsed, which is
     * why the registry gives it a code of its own: there is no field to point at.
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
     * Constraints declared directly on handler method parameters — here, the {@code Idempotency-Key}
     * header. Same {@code code} and same shape as a rejected body: the caller cannot tell, and should
     * not have to.
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
     * Spring MVC exception nobody anticipated from going out without a {@code code} and a
     * {@code traceId}.
     *
     * <p>It never overwrites a {@code code} that is already there: a handler that knows <em>which</em>
     * failure this is knows better than a lookup by status.
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
