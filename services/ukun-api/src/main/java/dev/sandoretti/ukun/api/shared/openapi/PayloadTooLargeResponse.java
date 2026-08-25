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
 * Publishes the {@code 413} of {@code docs/12-error-contract.md} on the one operation that can answer
 * it: the product image upload of RF-10. See {@link BadRequestResponse}.
 *
 * <p>The response may be written by the gateway rather than by this service, which cuts an oversized
 * request before it arrives ({@code docs/08-identidad-y-seguridad.md} §9). It is documented here all
 * the same, because what the contract describes is what the client receives — and both emitters
 * produce the same body on purpose.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ApiResponse(
        responseCode = "413",
        description = "`PAYLOAD_TOO_LARGE`. The upload is over the limit; `maxBytes` says which.",
        content =
                @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ProblemDetail.class)))
public @interface PayloadTooLargeResponse {}
