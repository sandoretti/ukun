package dev.sandoretti.ukun.api.catalog.web;

import dev.sandoretti.ukun.api.catalog.application.ProductTypeQueryService;
import dev.sandoretti.ukun.api.catalog.application.ProductTypeResponse;
import java.util.List;
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
@RequestMapping("/api/product-types")
class ProductTypeController {

    private final ProductTypeQueryService types;

    ProductTypeController(ProductTypeQueryService types) {
        this.types = types;
    }

    /** The method name is the {@code operationId} springdoc publishes; the SPA's client is named after it. */
    @GetMapping
    List<ProductTypeResponse> listProductTypes() {
        return types.list();
    }
}
