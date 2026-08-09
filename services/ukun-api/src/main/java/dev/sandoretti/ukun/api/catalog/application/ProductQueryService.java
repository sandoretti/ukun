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
import org.springframework.data.domain.Sort;
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

    /**
     * The order of the typeahead: by name, so the dropdown does not reshuffle between two keystrokes
     * that match the same rows, with {@code id} as the last criterion — defect #23, and the reason
     * every {@code Sort} of this API is total. Without it, a {@code limit} applied over tied names is
     * free to return a different subset each time the same letters are typed.
     *
     * <p>How two accented names compare is the database's collation and not ours, the same call
     * {@code ProductSpecifications} makes for accent folding. Worth knowing rather than worth
     * overriding here: under a {@code C} locale {@code Litera} sorts before {@code Lámpara}, which is
     * not what a Spanish reader expects. If that is ever to change it is the collation of the column
     * that changes, in a changeset a person reviews — not a {@code COLLATE} spliced into one query.
     */
    private static final Sort BY_NAME =
            Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id"));

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
     * The typeahead of the search box (RF-01, {@code suggestProducts}).
     *
     * <p><strong>A list and never a {@code Page}.</strong> The contract declares a flat array with no
     * wrapper, and that is a performance decision rather than a shape preference: a page carries
     * {@code totalElements}, {@code totalElements} means a {@code COUNT} over the whole match, and this
     * endpoint fires on every keystroke. Counting a result nobody paginates is exactly the cost the
     * autocomplete exists to avoid.
     *
     * <p>Hence the fluent query rather than {@code findAll(spec, PageRequest.of(0, limit))}: it applies
     * the limit without ever asking how many rows there were. It is also why no {@code @EntityGraph}
     * appears anywhere near it — {@link ProductSuggestionResponse} carries no type, so nothing reads
     * the association and there is no N+1 to prevent.
     *
     * <p>The specifications are {@link ProductSpecifications#isActive()} and
     * {@link ProductSpecifications#nameContains(String)}, the very same two the listing composes. A
     * second search query written for this endpoint would be a second answer to "what does typing
     * {@code sofa} match", and the two would drift the first time one of them was fixed — accent
     * folding (defect #22) and the escaping of {@code %} and {@code _} included.
     */
    public List<ProductSuggestionResponse> suggest(String term, int limit) {
        Specification<Product> matches = Specification.allOf(
                ProductSpecifications.isActive(), ProductSpecifications.nameContains(term));

        return products
                .findBy(matches, query -> query.sortBy(BY_NAME).limit(limit).all())
                .stream()
                .map(mapper::toSuggestion)
                .toList();
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
