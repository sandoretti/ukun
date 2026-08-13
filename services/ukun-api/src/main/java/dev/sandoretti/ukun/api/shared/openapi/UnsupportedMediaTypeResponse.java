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
 * Publishes the {@code 415} of {@code docs/12-error-contract.md} on the product image upload of RF-10.
 * See {@link BadRequestResponse}.
 *
 * <p>What it reports is a judgement about the <em>content</em> of the file, not about the
 * {@code Content-Type} header Spring itself negotiates: the upload is rejected because its first bytes
 * are not those of a JPEG, a PNG or a WebP ({@code docs/08-identidad-y-seguridad.md} §9). A
 * {@code .jpg} that is really something else is exactly the case, and it is why the check cannot be
 * left to content negotiation.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ApiResponse(
        responseCode = "415",
        description = "`UNSUPPORTED_MEDIA_TYPE`. The file's real content is not an allowed image format.",
        content =
                @Content(
                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                        schema = @Schema(implementation = ProblemDetail.class)))
public @interface UnsupportedMediaTypeResponse {}
