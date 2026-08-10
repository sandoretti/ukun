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
 * Publishes the {@code 401} of {@code docs/12-error-contract.md} on an operation that needs a token.
 *
 * <p>It arrives with the first protected endpoints of the service ({@code /api/me}, v0.4). Until then
 * every operation was anonymous, so the package only needed the three codes a public read can answer
 * with — the reasoning for why any of these annotations exist at all is in {@link BadRequestResponse}
 * and is not repeated here.
 *
 * <p>The body is written by the filter chain rather than by {@code GlobalExceptionHandler}: a token
 * that is missing or invalid is rejected before any controller is chosen, and
 * {@code ProblemDetailAuthenticationHandler} is what renders it. Same shape either way, which is the
 * point of that class.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ApiResponse(
        responseCode = "401",
        description = "`UNAUTHENTICATED`. The token is missing, expired, or not for this service.",
        content =
                @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ProblemDetail.class)))
public @interface UnauthenticatedResponse {}
