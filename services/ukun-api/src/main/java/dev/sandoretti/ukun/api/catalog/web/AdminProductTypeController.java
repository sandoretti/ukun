package dev.sandoretti.ukun.api.catalog.web;

import dev.sandoretti.ukun.api.catalog.application.AdminProductTypeResponse;
import dev.sandoretti.ukun.api.catalog.application.ProductTypeAdminService;
import dev.sandoretti.ukun.api.catalog.application.ProductTypeRequest;
import dev.sandoretti.ukun.api.shared.openapi.ApiSecurityScheme;
import dev.sandoretti.ukun.api.shared.openapi.BadRequestResponse;
import dev.sandoretti.ukun.api.shared.openapi.ConflictResponse;
import dev.sandoretti.ukun.api.shared.openapi.ForbiddenResponse;
import dev.sandoretti.ukun.api.shared.openapi.InternalErrorResponse;
import dev.sandoretti.ukun.api.shared.openapi.NotFoundResponse;
import dev.sandoretti.ukun.api.shared.openapi.UnauthenticatedResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
 * The back-office CRUD of product types (RF-13). {@code ADMIN} only.
 *
 * <p><strong>Under {@code /api/admin/**}, and that is the rule rather than a choice of prefix</strong>
 * (ADR-0020): every admin-only operation hangs off it and only they do, which is what lets the gateway
 * express its coarse filter as a single rule instead of a list of routes. Stock management is the
 * counter-example and stays outside, because it belongs to any employee.
 *
 * <p>The {@code @PreAuthorize} is not redundant with that gateway rule: {@code ukun-api} validates the
 * token itself and is the final authority, never trusting a header injected upstream
 * ({@code docs/08-identidad-y-seguridad.md} §4). Two layers, and the outer one is defence in depth.
 *
 * <p>A controller of its own rather than methods on {@code ProductTypeController}: the public read and
 * this are two resources at two URLs with two audiences, and the {@code AdminProductType} they answer
 * with is not the public {@code ProductType} either.
 */
@RestController
@RequestMapping(path = "/api/admin/product-types", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = ApiSecurityScheme.BEARER_AUTH)
class AdminProductTypeController {

    private final ProductTypeAdminService types;

    AdminProductTypeController(ProductTypeAdminService types) {
        this.types = types;
    }

    /**
     * A flat array like the public one, and for the same reason: a small closed set the SPA caches
     * whole. What it adds is {@code productCount}, which is what tells the screen whether a type can be
     * deleted before the admin tries.
     */
    @GetMapping
    @ApiResponse(responseCode = "200", description = "Product types, sorted by name.")
    @UnauthenticatedResponse
    @ForbiddenResponse
    @InternalErrorResponse
    List<AdminProductTypeResponse> listProductTypesForAdmin() {
        return types.list();
    }

    @GetMapping("/{id}")
    @ApiResponse(responseCode = "200", description = "The product type.")
    @UnauthenticatedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @InternalErrorResponse
    AdminProductTypeResponse getProductType(@PathVariable Long id) {
        return types.get(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponse(responseCode = "201", description = "Product type created.")
    @BadRequestResponse
    @UnauthenticatedResponse
    @ForbiddenResponse
    @ConflictResponse
    @InternalErrorResponse
    ResponseEntity<AdminProductTypeResponse> createProductType(@Valid @RequestBody ProductTypeRequest request) {
        AdminProductTypeResponse created = types.create(request);

        return ResponseEntity.created(URI.create("/api/admin/product-types/" + created.id()))
                .body(created);
    }

    /** The name is the only editable field, so this is a rename and nothing else. */
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponse(responseCode = "200", description = "The renamed type.")
    @BadRequestResponse
    @UnauthenticatedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @ConflictResponse
    @InternalErrorResponse
    AdminProductTypeResponse updateProductType(@PathVariable Long id, @Valid @RequestBody ProductTypeRequest request) {
        return types.rename(id, request);
    }

    /** A real delete, possible only while the type has no products (ADR-0013). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "Product type deleted.")
    @UnauthenticatedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @ConflictResponse
    @InternalErrorResponse
    void deleteProductType(@PathVariable Long id) {
        types.delete(id);
    }
}
