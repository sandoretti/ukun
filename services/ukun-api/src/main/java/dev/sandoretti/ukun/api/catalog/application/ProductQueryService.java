package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.catalog.domain.Product;
import dev.sandoretti.ukun.api.catalog.infra.ProductRepository;
import dev.sandoretti.ukun.api.catalog.infra.ProductSpecifications;
import dev.sandoretti.ukun.api.shared.web.PageResponse;
import dev.sandoretti.ukun.api.shared.web.PaginationParams;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read side of the catalog (RF-01, query Q1).
 *
 * <p>{@code @Transactional(readOnly = true)} at class level, per the skill {@code spring-api}: with
 * {@code open-in-view: false} any lazy access leaking outside this transaction blows up in a test
 * instead of quietly degrading into an N+1 in production, which is what today holds the legacy
 * {@code pedidos.html} together without anyone having decided it.
 *
 * <p>The constructor does nothing but assign. That is rule 6 and it is a real legacy defect: two
 * services look up {@code tienda.nombre = 'Ukun Online'} in their constructor and the application
 * fails to start if the row is missing. Data dependencies are resolved lazily, inside the business
 * transaction.
 */
@Service
@Transactional(readOnly = true)
public class ProductQueryService {

    private final ProductRepository products;
    private final ProductMapper mapper;

    public ProductQueryService(ProductRepository products, ProductMapper mapper) {
        this.products = products;
        this.mapper = mapper;
    }

    public PageResponse<ProductSummaryResponse> list(ProductFilters filters, PaginationParams pagination) {
        Page<Product> page =
                products.findAll(specificationFor(filters), pagination.toPageable(filters.sort().toSort()));

        return PageResponse.of(page, mapper::toSummary);
    }

    /**
     * {@code isActive()} is the base and not one more optional filter: the public catalog never shows
     * a discontinued product (ADR-0013). Everything else is added only when the request carried it,
     * which is what makes one paginated method enough for sixteen filter combinations.
     */
    private static Specification<Product> specificationFor(ProductFilters filters) {
        Specification<Product> specification = ProductSpecifications.isActive();

        if (filters.name() != null) {
            specification = specification.and(ProductSpecifications.nameContains(filters.name()));
        }
        if (filters.typeId() != null) {
            specification = specification.and(ProductSpecifications.hasType(filters.typeId()));
        }
        if (filters.priceMin() != null) {
            specification = specification.and(ProductSpecifications.priceAtLeast(filters.priceMin()));
        }
        if (filters.priceMax() != null) {
            specification = specification.and(ProductSpecifications.priceAtMost(filters.priceMax()));
        }

        return specification;
    }
}
