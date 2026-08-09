package dev.sandoretti.ukun.api.stores.infra;

import dev.sandoretti.ukun.api.shared.persistence.TextSearch;
import dev.sandoretti.ukun.api.stores.domain.Store;
import org.springframework.data.jpa.domain.Specification;

/**
 * The one optional predicate of the store listing. Composed by the query service and applied only
 * when the request carries {@code q}, the same shape {@code docs/03-analisis-y-patrones.md} §2
 * prescribes over a family of derived queries.
 */
public final class StoreSpecifications {

    private static final String NAME = "name";
    private static final String ADDRESS = "address";
    private static final String CITY = "city";

    private StoreSpecifications() {}

    /**
     * {@code q} of the contract: "Optional filter by name or city". One term, two columns, {@code OR}
     * — a customer typing "Barcelona" is looking for the store there, and one typing "Ukun Online" is
     * looking for it by name.
     *
     * <p>Accent-insensitive through {@link TextSearch}, and that is not theoretical here: three of the
     * four seeded stores live in {@code Alcalá de Henares}, {@code Torrejón de Ardoz} and
     * {@code Barcelona}, so a plain {@code LIKE} would answer nothing to two thirds of the obvious
     * searches (defect #22).
     *
     * <p>The city predicate reaches through {@code address} with {@code root.get(…).get(…)}, which
     * Hibernate resolves as an inner join. That is correct rather than merely convenient:
     * {@code address_id} is {@code NOT NULL}, so no store can be dropped by it.
     */
    public static Specification<Store> matches(String term) {
        return (root, query, builder) -> builder.or(
                TextSearch.containsIgnoringAccents(builder, root.get(NAME), term),
                TextSearch.containsIgnoringAccents(builder, root.get(ADDRESS).get(CITY), term));
    }
}
