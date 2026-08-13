package dev.sandoretti.ukun.api.catalog.web;

import dev.sandoretti.ukun.api.catalog.application.ProductImageResponse;
import dev.sandoretti.ukun.api.catalog.application.ProductImageService;
import dev.sandoretti.ukun.api.shared.openapi.ApiSecurityScheme;
import dev.sandoretti.ukun.api.shared.openapi.BadRequestResponse;
import dev.sandoretti.ukun.api.shared.openapi.ForbiddenResponse;
import dev.sandoretti.ukun.api.shared.openapi.InternalErrorResponse;
import dev.sandoretti.ukun.api.shared.openapi.NotFoundResponse;
import dev.sandoretti.ukun.api.shared.openapi.PayloadTooLargeResponse;
import dev.sandoretti.ukun.api.shared.openapi.UnauthenticatedResponse;
import dev.sandoretti.ukun.api.shared.openapi.UnsupportedMediaTypeResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The photo of a product (RF-10) — <strong>the only multipart surface of the whole service</strong>.
 *
 * <p>A controller of its own, and that is the shape {@code docs/08-identidad-y-seguridad.md} §9 asks
 * for: the image is a sub-resource with its own URL, so the hardening it needs — content sniffing, a
 * size limit, a server-generated name — lives in one place instead of being a branch inside the product
 * CRUD. Everything else this service accepts is JSON.
 *
 * <p>{@code ADMIN} only, like the rest of {@code /api/admin/**} (ADR-0020).
 *
 * <p>The path is {@code /api/admin/products/{id}/image} and not a sibling of the product, because that
 * is what a sub-resource is: it is created by writing to it, it is removed by deleting it, and neither
 * of the two is an edit of the product's own representation.
 */
@RestController
@RequestMapping(path = "/api/admin/products/{id}/image", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = ApiSecurityScheme.BEARER_AUTH)
class ProductImageController {

    private final ProductImageService images;

    ProductImageController(ProductImageService images) {
        this.images = images;
    }

    /**
     * Uploads or replaces the photo.
     *
     * <p>The bytes are read here and handed on as a {@code byte[]}: the file is 5 MB at most by
     * configuration, the format check needs its first bytes anyway, and a {@code MultipartFile} reaching
     * the service would tie a use case to a servlet type it has no business knowing — the same reason
     * the filters of this API are records rather than {@code HttpServletRequest}.
     *
     * <p>{@code IOException} while reading a part that Spring has already buffered is not a client
     * error and has no {@code code} of its own: it is wrapped so the advice reports it as the
     * {@code 500} it is, with a {@code traceId}, instead of being declared as a checked failure this
     * signature would have to carry upward.
     */
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiResponse(responseCode = "200", description = "Image stored; the previous one, if any, has been deleted.")
    @BadRequestResponse
    @UnauthenticatedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @PayloadTooLargeResponse
    @UnsupportedMediaTypeResponse
    @InternalErrorResponse
    ProductImageResponse uploadProductImage(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        try {
            return images.replace(id, file.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded part", e);
        }
    }

    /** Idempotent: a product with no image also answers {@code 204}. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "Image removed, or there was none.")
    @UnauthenticatedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @InternalErrorResponse
    void deleteProductImage(@PathVariable Long id) {
        images.remove(id);
    }
}
