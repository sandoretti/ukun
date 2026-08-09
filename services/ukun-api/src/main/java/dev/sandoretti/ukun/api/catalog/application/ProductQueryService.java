package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.catalog.domain.Product;
import dev.sandoretti.ukun.api.catalog.infra.ProductRepository;
import dev.sandoretti.ukun.api.catalog.infra.ProductSpecifications;
import dev.sandoretti.ukun.api.shared.error.ResourceNotFoundException;
import dev.sandoretti.ukun.api.shared.web.PageResponse;
import dev.sandoretti.ukun.api.shared.web.PaginationParams;
import java.util.ArrayList;
import java.util.List;
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
     * The product detail (RF-01, {@code getProduct}).
     *
     * <p>A discontinued product is a {@code 404} and not a {@code 200} carrying a flag: it is out of
     * the public catalog (ADR-0013), so as far as this API is concerned it does not exist. Filtering
     * after the look-up rather than inside the query is deliberate — the row is fetched by primary
     * key, so there is nothing to save, and the rule stays visible in the use case instead of being
     * buried in a method name.
     *
     * <p>{@code orElseThrow} and never a {@code null} return (rule 5 of the skill {@code spring-api}):
     * the exception already carries its {@code code}, its status and the {@code resource}/{@code id}
     * extensions, and the advice renders it. That is what replaces the legacy habit of returning
     * {@code null} and re-testing for it in every controller (defect #11).
     */
    public ProductDetailResponse detail(Long id) {
        return products
                .findById(id)
                .filter(Product::isActive)
                .map(mapper::toDetail)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    /**
     * {@code isActive()} is the base and not one more optional filter: the public catalog never shows
     * a discontinued product (ADR-0013). Everything else joins the list only when the request carried
     * it, which is what makes one paginated method enough for sixteen filter combinations.
     *
     * <p>{@link Specification#allOf(Iterable)} rather than a variable reassigned through a chain of
     * {@code and(…)}: the list says "these are the conditions" and adding a filter tomorrow is one
     * line that cannot accidentally drop the accumulated specification.
     */
    private static Specification<Product> specificationFor(ProductFilters filters) {
        List<Specification<Product>> conditions = new ArrayList<>();
        conditions.add(ProductSpecifications.isActive());

        if (filters.name() != null) {
            conditions.add(ProductSpecifications.nameContains(filters.name()));
        }
        if (filters.typeId() != null) {
            conditions.add(ProductSpecifications.hasType(filters.typeId()));
        }
        if (filters.priceMin() != null) {
            conditions.add(ProductSpecifications.priceAtLeast(filters.priceMin()));
        }
        if (filters.priceMax() != null) {
            conditions.add(ProductSpecifications.priceAtMost(filters.priceMax()));
        }

        return Specification.allOf(conditions);
    }
}
