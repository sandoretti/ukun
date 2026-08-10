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
 * Publishes the {@code 403} of {@code docs/12-error-contract.md}: authenticated, but not allowed.
 *
 * <p>It covers both halves of that code and the distinction is worth keeping in mind when declaring it
 * — the wrong role, decided by {@code @PreAuthorize} before the method runs, and somebody else's
 * resource, decided by the domain inside it ({@code docs/08-identidad-y-seguridad.md} §4). An operation
 * declares this annotation if either can happen to it.
 *
 * <p>See {@link BadRequestResponse} for why springdoc cannot work these out on its own.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ApiResponse(
        responseCode = "403",
        description = "`FORBIDDEN`. The wrong role, or somebody else's resource.",
        content =
                @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ProblemDetail.class)))
public @interface ForbiddenResponse {}
