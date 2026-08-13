package dev.sandoretti.ukun.api.catalog.web;

import dev.sandoretti.ukun.api.catalog.application.AdminProductFilters;
import dev.sandoretti.ukun.api.catalog.application.AdminProductResponse;
import dev.sandoretti.ukun.api.catalog.application.CreateProductRequest;
import dev.sandoretti.ukun.api.catalog.application.ProductAdminService;
import dev.sandoretti.ukun.api.catalog.application.UpdateProductRequest;
import dev.sandoretti.ukun.api.shared.openapi.ApiSecurityScheme;
import dev.sandoretti.ukun.api.shared.openapi.BadRequestResponse;
import dev.sandoretti.ukun.api.shared.openapi.ConflictResponse;
import dev.sandoretti.ukun.api.shared.openapi.ForbiddenResponse;
import dev.sandoretti.ukun.api.shared.openapi.InternalErrorResponse;
import dev.sandoretti.ukun.api.shared.openapi.NotFoundResponse;
import dev.sandoretti.ukun.api.shared.openapi.UnauthenticatedResponse;
import dev.sandoretti.ukun.api.shared.web.PageResponse;
import dev.sandoretti.ukun.api.shared.web.PaginationParams;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The back-office CRUD of products (RF-10). {@code ADMIN} only, under {@code /api/admin/**} for the
 * reason ADR-0020 gives — see {@link AdminProductTypeController}, which says it once.
 *
 * <p>A controller of its own next to {@code ProductController}, not more methods on it: the public
 * catalogue is anonymous and cacheable and this is neither, and mixing them would put an
 * {@code @PreAuthorize} on some methods of a class whose others must stay open — the arrangement where
 * a new endpoint gets protected by remembering to.
 *
 * <p><strong>{@code :discontinue} and {@code :reactivate} are {@code POST}s on a sub-path with a
 * colon</strong>, not {@code PATCH}es of a field, and the shape follows the meaning: they are business
 * operations with their own audit trail and their own events (ADR-0013), which is why they are not a
 * checkbox on the edit form. The colon syntax is the contract's, and Spring maps it as an ordinary
 * literal path segment.
 */
@RestController
@RequestMapping(path = "/api/admin/products", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = ApiSecurityScheme.BEARER_AUTH)
class AdminProductController {

    private final ProductAdminService products;

    AdminProductController(ProductAdminService products) {
        this.products = products;
    }

    /**
     * The listing that can see discontinued products — {@code status} decides, and defaults to
     * {@code ACTIVE} so the commonest screen agrees with the public catalogue.
     *
     * <p>{@code @ParameterObject} on both bound objects, for the reason {@code ProductController}
     * documents: without it springdoc publishes two opaque parameters called {@code filters} and
     * {@code pagination}, and {@code OpenApiDriftIT} stops matching the frozen contract while the
     * endpoint keeps working.
     */
    @GetMapping
    @ApiResponse(responseCode = "200", description = "A page of products, active or not depending on `status`.")
    @BadRequestResponse
    @UnauthenticatedResponse
    @ForbiddenResponse
    @InternalErrorResponse
    PageResponse<AdminProductResponse> listProductsForAdmin(
            @Valid @ParameterObject AdminProductFilters filters, @Valid @ParameterObject PaginationParams pagination) {

        return products.list(filters, pagination);
    }

    @GetMapping("/{id}")
    @ApiResponse(responseCode = "200", description = "The product.")
    @UnauthenticatedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @InternalErrorResponse
    AdminProductResponse getProductForAdmin(@PathVariable Long id) {
        return products.detail(id);
    }

    /** Born active and without a photo; {@code Location} points at the back-office view of it. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponse(responseCode = "201", description = "Product created, active, with `imageUrl: null`.")
    @BadRequestResponse
    @UnauthenticatedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @ConflictResponse
    @InternalErrorResponse
    ResponseEntity<AdminProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        AdminProductResponse created = products.create(request);

        return ResponseEntity.created(URI.create("/api/admin/products/" + created.id()))
                .body(created);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponse(responseCode = "200", description = "The updated product.")
    @BadRequestResponse
    @UnauthenticatedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @InternalErrorResponse
    AdminProductResponse updateProduct(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return products.update(id, request);
    }

    /** Only ever possible for a product nothing has referenced; the real way out is discontinuing it. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "Product deleted, along with its image.")
    @UnauthenticatedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @ConflictResponse
    @InternalErrorResponse
    void deleteProduct(@PathVariable Long id) {
        products.delete(id);
    }

    /** Idempotent: a product already out of the catalogue answers {@code 200} and nothing changes. */
    @PostMapping("/{id}:discontinue")
    @ApiResponse(responseCode = "200", description = "The product, now with `isActive: false`.")
    @UnauthenticatedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @InternalErrorResponse
    AdminProductResponse discontinueProduct(@PathVariable Long id) {
        return products.discontinue(id);
    }

    /** The inverse, equally idempotent. Availability is a different question (RF-01). */
    @PostMapping("/{id}:reactivate")
    @ApiResponse(responseCode = "200", description = "The product, now with `isActive: true`.")
    @UnauthenticatedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @InternalErrorResponse
    AdminProductResponse reactivateProduct(@PathVariable Long id) {
        return products.reactivate(id);
    }
}
