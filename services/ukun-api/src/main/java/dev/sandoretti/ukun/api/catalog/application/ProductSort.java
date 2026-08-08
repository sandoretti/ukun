package dev.sandoretti.ukun.api.catalog.application;

import org.springframework.data.domain.Sort;

/**
 * The {@code sort} parameter of {@code GET /api/products} — a closed enum, exactly as the contract
 * declares it, and deliberately <strong>not</strong> Spring Data's {@code sort=field,direction}. The
 * frontend generates its type from this enum, and it must not start depending on which entity fields
 * happen to exist.
 *
 * <p>Every value appends {@code id} as its final criterion. That is defect #23, and it is not
 * theoretical: {@code Litera infantil} and {@code Sofá de cuero} both cost {@code 399.99} in the seed
 * catalog, and Postgres is free to return tied rows in a different order for each page. With
 * {@code Page<T>}, that means the same product showing up on page 1 and page 2 — or on neither.
 * The tie-breaker costs nothing and makes pagination total.
 */
public enum ProductSort {
    PRICE_ASC(Sort.Direction.ASC),
    PRICE_DESC(Sort.Direction.DESC);

    /** What the contract declares as the default of the {@code ProductSort} schema. */
    public static final ProductSort DEFAULT = PRICE_ASC;

    private static final String PRICE = "price";
    private static final String ID = "id";

    private final Sort.Direction direction;

    ProductSort(Sort.Direction direction) {
        this.direction = direction;
    }

    public Sort toSort() {
        return Sort.by(direction, PRICE).and(Sort.by(Sort.Direction.ASC, ID));
    }
}
