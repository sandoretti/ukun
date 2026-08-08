package dev.sandoretti.ukun.api.catalog.web;

import dev.sandoretti.ukun.api.catalog.application.ProductFilters;
import dev.sandoretti.ukun.api.catalog.application.ProductQueryService;
import dev.sandoretti.ukun.api.catalog.application.ProductSummaryResponse;
import dev.sandoretti.ukun.api.shared.web.PageResponse;
import dev.sandoretti.ukun.api.shared.web.PaginationParams;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public catalog (RF-01), {@code listProducts} of the contract. No authentication: it is the
 * anonymous, cacheable read of the shop.
 *
 * <p>It delegates and nothing else. It holds no {@code @Transactional} (that belongs to the service),
 * it never touches a repository (ArchUnit watches that), and it contains no {@code if (x == null)} —
 * a service either returns a value or throws a typed exception, and the advice renders it.
 *
 * <p>Both parameter objects are bound from the query string and validated. Their bounds are the
 * contract's, and the {@code 400} they produce comes back as a {@code ProblemDetail} with
 * {@code code: VALIDATION_FAILED} and one {@code errors[]} entry per offending field.
 */
@RestController
@RequestMapping("/api/products")
class ProductController {

    private final ProductQueryService products;

    ProductController(ProductQueryService products) {
        this.products = products;
    }

    @GetMapping
    PageResponse<ProductSummaryResponse> listProducts(
            @Valid ProductFilters filters, @Valid PaginationParams pagination) {

        return products.list(filters, pagination);
    }
}
