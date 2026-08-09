package dev.sandoretti.ukun.api.catalog.infra;

import dev.sandoretti.ukun.api.catalog.domain.Product;
import dev.sandoretti.ukun.api.shared.persistence.TextSearch;
import java.math.BigDecimal;
import org.springframework.data.jpa.domain.Specification;

/**
 * The composable predicates of the catalog listing. Each optional filter is one of these, and the
 * query service {@code and}s together only the ones the request actually carries — the alternative
 * being the combinatorial explosion of derived queries that {@code docs/03-analisis-y-patrones.md}
 * §2 rules out.
 */
public final class ProductSpecifications {

    private static final String NAME = "name";
    private static final String PRICE = "price";
    private static final String IS_ACTIVE = "isActive";
    private static final String PRODUCT_TYPE = "productType";
    private static final String ID = "id";

    private ProductSpecifications() {}

    /**
     * Not an optional filter: the public catalog only ever returns active products. Discontinuation
     * is logical (ADR-0013), and seeing discontinued ones is {@code GET /api/admin/products}, which
     * belongs to v0.7.
     */
    public static Specification<Product> isActive() {
        return (root, query, builder) -> builder.isTrue(root.get(IS_ACTIVE));
    }

    /**
     * Defect #22. Searching {@code "sofa"} has to find {@code "Sofá de cuero"}, and {@code "cafe"}
     * has to find {@code "Mesa de café"} — the seed catalog is full of accents, so a plain
     * {@code LIKE} silently returns nothing for half the words a customer types.
     *
     * <p>The folding itself is {@link TextSearch}, in {@code shared}: the store listing searches by
     * name and city the same way, and the escape rule is subtle enough that a second copy of it would
     * eventually stop matching this one.
     */
    public static Specification<Product> nameContains(String term) {
        return (root, query, builder) -> TextSearch.containsIgnoringAccents(builder, root.get(NAME), term);
    }

    /** Equality against the foreign key: it needs no join, the column is on {@code product}. */
    public static Specification<Product> hasType(Long typeId) {
        return (root, query, builder) -> builder.equal(root.get(PRODUCT_TYPE).get(ID), typeId);
    }

    /** Inclusive, as the contract declares ("Minimum price, inclusive"). */
    public static Specification<Product> priceAtLeast(BigDecimal min) {
        return (root, query, builder) -> builder.greaterThanOrEqualTo(root.get(PRICE), min);
    }

    /** Inclusive, as the contract declares ("Maximum price, inclusive"). */
    public static Specification<Product> priceAtMost(BigDecimal max) {
        return (root, query, builder) -> builder.lessThanOrEqualTo(root.get(PRICE), max);
    }
}
