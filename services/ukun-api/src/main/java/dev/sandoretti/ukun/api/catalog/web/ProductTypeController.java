package dev.sandoretti.ukun.api.catalog.web;

import dev.sandoretti.ukun.api.catalog.application.ProductTypeQueryService;
import dev.sandoretti.ukun.api.catalog.application.ProductTypeResponse;
import dev.sandoretti.ukun.api.shared.openapi.InternalErrorResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/product-types} (RF-01). No authentication: like the catalog itself, it is an
 * anonymous, cacheable read.
 *
 * <p>A controller of its own and not one more method on {@code ProductController}, because
 * {@code /api/product-types} is a resource of its own and not a sub-resource of {@code /api/products}
 * — the contract puts it at the root for that reason. A class-level {@code @RequestMapping} that only
 * ever prefixes one path is worth less than the resource-per-controller shape it keeps.
 *
 * <p>No parameters, so nothing to bind and nothing to validate: a flat array of a small closed set,
 * which the SPA caches whole. Managing these types (creating, renaming, deleting) is RF-13 and lives
 * under {@code /api/admin/product-types} (ADR-0020), which is v0.7.
 */
@RestController
@RequestMapping(path = "/api/product-types", produces = MediaType.APPLICATION_JSON_VALUE)
class ProductTypeController {

    private final ProductTypeQueryService types;

    ProductTypeController(ProductTypeQueryService types) {
        this.types = types;
    }

    /**
     * The method name is the {@code operationId} springdoc publishes; the SPA's client is named after
     * it.
     *
     * <p>The only operation of the contract with a single error status: nothing is bound here, so there
     * is no {@code 400} to declare.
     */
    @GetMapping
    @ApiResponse(responseCode = "200", description = "Product types, sorted by name.")
    @InternalErrorResponse
    List<ProductTypeResponse> listProductTypes() {
        return types.list();
    }
}
