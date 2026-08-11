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
 * Publishes the {@code 409} of {@code docs/12-error-contract.md}. See {@link BadRequestResponse}.
 *
 * <p>Several codes share this status, and which of them an operation can answer is the operation's
 * business — checkout emits {@code INSUFFICIENT_STOCK} and {@code IDEMPOTENCY_IN_PROGRESS}. The
 * document publishes the status and the media type; the {@code code} is what the frontend switches on,
 * and it is documented in the registry rather than in the schema.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ApiResponse(
        responseCode = "409",
        description = "A conflict with the current state; see the `code` of the body.",
        content =
                @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ProblemDetail.class)))
public @interface ConflictResponse {}
