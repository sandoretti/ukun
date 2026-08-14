package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.catalog.CatalogApi;
import dev.sandoretti.ukun.api.catalog.ProductSummary;
import dev.sandoretti.ukun.api.catalog.domain.Product;
import dev.sandoretti.ukun.api.catalog.infra.ProductRepository;
import dev.sandoretti.ukun.api.catalog.infra.ProductSpecifications;
import dev.sandoretti.ukun.api.shared.error.ResourceNotFoundException;
import dev.sandoretti.ukun.api.shared.web.PageResponse;
import dev.sandoretti.ukun.api.shared.web.PaginationParams;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
public class ProductQueryService implements CatalogApi {

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
     * The same detail resolved by the identifier the employee has in front of them, printed on the
     * label (RF-09, {@code getProductBySku}, ADR-0015). It is the step before creating a stock line:
     * the SKU is typed, this resolves it, and only then does {@code POST /api/stores/{storeId}/stock}
     * get called.
     *
     * <p>A dedicated read and not a filter over the listing, because the SKU is unique: the answer is a
     * resource or a {@code 404}, never a page of zero or one elements the caller has to unwrap.
     *
     * <p>The active rule is {@link #detail(Long)}'s, applied the same way and for the same reason — a
     * discontinued product is out of the public catalog (ADR-0013), whichever key you look it up by,
     * and here that is precisely what stops stock being added to something the shop has withdrawn. The
     * {@code 404} is also indistinguishable from a SKU that never existed, which is what keeps the
     * response from telling anyone who asks which codes the shop used to sell.
     *
     * <p>Not written on top of {@link #activeBySku(String)}, which answers the neighbouring question
     * for another module: that one returns a {@link ProductSummary} with no product type, and reusing it
     * here would mean either fetching the type twice or lending the module read a join it does not want.
     * The rule they share is spelled the same in both — {@code filter(Product::isActive)} over the row —
     * and it is one line, which is the honest way for two reads with different fetch plans to agree.
     *
     * <p>{@code id} in the {@code ProblemDetail} is the SKU: the registry types that extension as text
     * so one exception serves numeric identifiers, SKUs and UUIDs alike
     * ({@code docs/12-error-contract.md}).
     */
    public ProductDetailResponse detailBySku(String sku) {
        return products
                .findWithProductTypeBySku(sku)
                .filter(Product::isActive)
                .map(mapper::toDetail)
                .orElseThrow(() -> new ResourceNotFoundException("Product", sku));
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
     * {@link CatalogApi#summariesByIds(Set)}: what the modules that hold a {@code product_id} are
     * allowed to know about it.
     *
     * <p>{@code findAllById} rather than a query of this module's own. It is one statement for the whole
     * batch, it is served by the primary key, and it does not carry the {@code @EntityGraph} the two
     * overrides in {@link ProductRepository} do — which here is the point rather than an omission:
     * {@link ProductSummary} has no product type, so fetching that association would be a join nobody
     * reads on the one read that runs over a whole cart. Its photo does not change that, and this is the
     * distinction worth keeping straight: {@code image_key} is a column of the row {@code findAllById}
     * already brings back, so it costs nothing; {@code product_type} is a second table.
     *
     * <p>An empty request is answered without touching the database. Spring Data would return an empty
     * list anyway, but the caller that has nothing to ask about is the commonest one there is — an empty
     * cart — and a statement issued to learn nothing is a statement the counter in {@code CartIT} would
     * be right to complain about.
     */
    @Override
    public Map<Long, ProductSummary> summariesByIds(Set<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }

        return products.findAllById(productIds).stream()
                .map(mapper::toModuleSummary)
                .collect(Collectors.toMap(ProductSummary::id, Function.identity()));
    }

    /**
     * {@link CatalogApi#activeBySku(String)}: the SKU an employee typed, resolved to the product they
     * may act on — the step {@code inventory} takes before creating a stock line (RF-09, ADR-0015).
     *
     * <p>The active filter is applied here and not left to the caller, for the reason the interface
     * gives. It reads the same way {@link #detail(Long)} does — {@code filter(Product::isActive)} over
     * the row — because it is the same rule: a discontinued product is invisible outside the back
     * office, whichever key you look it up by.
     *
     * <p>Backed by the {@code UNIQUE} on {@code product.sku} that ADR-0015 puts in the baseline, so it
     * is one indexed look-up and the return type can be an {@code Optional} rather than a list nobody
     * would know what to do with a second element of.
     */
    @Override
    public Optional<ProductSummary> activeBySku(String sku) {
        return products.findBySku(sku).filter(Product::isActive).map(mapper::toModuleSummary);
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
