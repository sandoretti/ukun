package dev.sandoretti.ukun.api.catalog.infra;

import dev.sandoretti.ukun.api.catalog.domain.Product;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
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

    /** Postgres extension added by {@code releases/v0.3/002-unaccent.yaml}. */
    private static final String UNACCENT = "unaccent";

    private static final char LIKE_ESCAPE = '\\';

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
     * <p>{@code unaccent(lower(…))} is applied on <strong>both</strong> sides in SQL rather than
     * normalising the term in Java with {@code java.text.Normalizer}. The two do not have to agree
     * on every character, and the side that decides what matches is the database's folding table,
     * not ours.
     *
     * <p>No index backs this and that is a decision, not an oversight: {@code unaccent()} is
     * {@code STABLE}, not {@code IMMUTABLE}, so it cannot be indexed without wrapping it in a
     * function of our own — and {@code docs/10-modelo-de-datos.md} §4 sets a written activation
     * threshold (~5,000 rows) instead of adding one just in case.
     */
    public static Specification<Product> nameContains(String term) {
        String pattern = "%" + escapeWildcards(term) + "%";

        return (root, query, builder) -> builder.like(
                normalised(builder, root.get(NAME)),
                normalised(builder, builder.literal(pattern)),
                LIKE_ESCAPE);
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

    private static Expression<String> normalised(CriteriaBuilder builder, Expression<String> value) {
        return builder.function(UNACCENT, String.class, builder.lower(value));
    }

    /**
     * A {@code %} or a {@code _} typed into the search box is a character the customer is looking
     * for, not a wildcard they meant to use. Escaping them keeps {@code "50%"} a literal search
     * instead of a query that matches the whole catalog.
     */
    private static String escapeWildcards(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
