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
 * Publishes the {@code 422} of {@code docs/12-error-contract.md} — {@code EMPTY_CART} and
 * {@code IDEMPOTENCY_MISMATCH}. See {@link BadRequestResponse}.
 *
 * <p>It is a request that parsed and validated and still cannot be carried out, which is the line
 * between this and the {@code 400} next door: an empty cart is a perfectly well-formed request to buy
 * nothing.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ApiResponse(
        responseCode = "422",
        description = "The request is well-formed but cannot be carried out; see the `code` of the body.",
        content =
                @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ProblemDetail.class)))
public @interface UnprocessableContentResponse {}
