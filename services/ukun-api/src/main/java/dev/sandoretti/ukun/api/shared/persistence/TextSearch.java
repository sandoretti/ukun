package dev.sandoretti.ukun.api.shared.persistence;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;

/**
 * Accent-insensitive substring matching, the shape defect #22 requires
 * ({@code docs/03-analisis-y-patrones.md} §1).
 *
 * <p>It lives in {@code shared} rather than next to the first {@code Specification} that needed it,
 * because the defect is not a property of the catalogue: it is the rule that <em>any</em> free-text
 * search in this API folds accents. Searching {@code "sofa"} has to find {@code "Sofá de cuero"} and
 * searching {@code "torrejon"} has to find the store in {@code "Torrejón de Ardoz"} — the seed data
 * is full of accents in both tables, so a plain {@code LIKE} silently returns nothing for half of
 * what a user types. Two copies of this method would be two chances for the escape character and the
 * {@code escape} argument of {@code LIKE} to drift apart, which is a bug nobody notices until a
 * search for {@code "50%"} returns the whole table.
 *
 * <p>{@code unaccent()} is a Postgres extension, created by
 * {@code db/changelog/releases/v0.3/002-unaccent.yaml}. No index backs it and that is a decision, not
 * an oversight: the function is {@code STABLE} rather than {@code IMMUTABLE}, so it cannot be indexed
 * without wrapping it in one of our own, and {@code docs/10-modelo-de-datos.md} §4 sets a written
 * activation threshold (~5,000 rows) instead of adding one just in case.
 */
public final class TextSearch {

    private static final String UNACCENT = "unaccent";

    private static final char LIKE_ESCAPE = '\\';

    private TextSearch() {}

    /**
     * {@code field LIKE ?}, with both sides folded to lower case and stripped of accents.
     *
     * <p>The folding happens in SQL on both sides rather than normalising the term in Java with
     * {@code java.text.Normalizer}: the two do not have to agree on every character, and the side
     * that decides what matches is the database's folding table, not ours.
     *
     * <p><strong>{@code value()} and not {@code literal()}, and the difference is not cosmetic.</strong>
     * {@code CriteriaBuilder.literal()} renders the term <em>into the SQL text</em> in Hibernate 6 and
     * 7 — {@code like unaccent(lower('%sofa%'))} — and no setting changes that
     * ({@code hibernate.criteria.literal_handling_mode=BIND} was tried and makes no difference).
     * {@link HibernateCriteriaBuilder#value} goes through {@code criteria.value_handling_mode}, whose
     * default is {@code BIND}, and comes out as a real JDBC parameter.
     *
     * <p>Two reasons, in order of how much they actually bite:
     *
     * <ul>
     *   <li><strong>One SQL string per distinct search term.</strong> Inlined, every keystroke on the
     *       typeahead mints a statement the database has never seen: Hibernate's plan cache and
     *       Postgres' prepared-statement cache both key on the text, so an anonymous endpoint anyone
     *       can call becomes an unbounded source of cache misses and plan churn. Bound, there is one
     *       statement for every search there will ever be.
     *   <li><strong>Injection, which the inlined version happened to survive.</strong> Hibernate doubles
     *       the single quotes when it inlines, so {@code ' OR 1=1 --} came back as zero rows and not as
     *       a leak — verified before this change. But the safety was Hibernate's rendering doing the
     *       right thing, one layer below anything this codebase asserts. A bound parameter never enters
     *       the SQL text at all, which is a property rather than a behaviour.
     * </ul>
     *
     * <p>The cast is to Hibernate's own builder because {@code value()} is not in the Jakarta
     * Persistence API. Nothing is lost by it: this method already calls {@code unaccent()}, a Postgres
     * extension, so the query is not portable in the first place.
     */
    public static Predicate containsIgnoringAccents(
            CriteriaBuilder builder, Expression<String> field, String term) {

        Expression<String> pattern =
                ((HibernateCriteriaBuilder) builder).value("%" + escapeWildcards(term) + "%");

        return builder.like(normalised(builder, field), normalised(builder, pattern), LIKE_ESCAPE);
    }

    private static Expression<String> normalised(CriteriaBuilder builder, Expression<String> value) {
        return builder.function(UNACCENT, String.class, builder.lower(value));
    }

    /**
     * A {@code %} or a {@code _} typed into a search box is a character the user is looking for, not
     * a wildcard they meant to use. Escaping them keeps {@code "50%"} a literal search instead of a
     * query that matches everything.
     */
    private static String escapeWildcards(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
