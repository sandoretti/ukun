package dev.sandoretti.ukun.api.shared.openapi;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Publishes the {@code 400} of {@code docs/12-error-contract.md} on an operation the contract declares
 * it for.
 *
 * <p><strong>Why the annotation is needed at all.</strong> The status codes an operation can answer
 * with are part of the contract, and springdoc only sees the happy path: the errors are rendered by
 * {@code GlobalExceptionHandler}, a {@code @RestControllerAdvice} it cannot introspect, so the
 * generated document declared a single {@code 200} for every endpoint. Which codes each operation has
 * is not derivable either — {@code GET /api/products/{id}} takes a parameter that can be malformed and
 * the contract still declares only {@code 404} and {@code 500} for it. So each operation states its
 * own, transcribed from the contract.
 *
 * <p>The three annotations of this package exist so that stating them is one word instead of five
 * lines of nested {@code @Content}. The body they point at is Spring's {@link ProblemDetail}, whose
 * five RFC 9457 fields are exactly right and whose extensions ({@code code}, {@code traceId},
 * {@code errors[]}) are not published — which is why {@code OpenApiDriftIT} compares the status code
 * and the media type of an error and not its schema.
 *
 * <p><strong>An operation that declares any of these must also declare its {@code 200}</strong> with a
 * bare {@code @ApiResponse(responseCode = "200", description = "…")}. Measured, not assumed: as soon as
 * one {@code @ApiResponse} is present, springdoc 3.0.3 publishes <em>only</em> the declared responses
 * and drops the success it had inferred from the return type — schema, media type and all. Declaring
 * the code back is enough for it to fill in the body it already knew, and {@code OpenApiDriftIT} is
 * what makes forgetting it a red build rather than a silently truncated document.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ApiResponse(
        responseCode = "400",
        description = "`VALIDATION_FAILED`, with the `errors[]` extension, or `MALFORMED_REQUEST`.",
        content =
                @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ProblemDetail.class)))
public @interface BadRequestResponse {}
