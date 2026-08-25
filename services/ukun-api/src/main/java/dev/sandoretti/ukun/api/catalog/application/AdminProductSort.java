package dev.sandoretti.ukun.api.catalog.application;

import org.springframework.data.domain.Sort;

/**
 * The {@code sort} parameter of {@code GET /api/admin/products} — a closed enum, like
 * {@link ProductSort} and for the same reason: the frontend generates its type from it and must not
 * start depending on which entity fields happen to exist.
 *
 * <p>It is a different enum rather than three more values on the public one because the working order
 * of a back-office is not a shopper's. An admin looks a product up by name and reviews what changed
 * last; a customer sorts by price. Merging them would publish {@code UPDATED_DESC} on a public
 * endpoint that has no {@code updatedAt} to show for it.
 *
 * <p>Every value appends {@code id} as its final criterion — defect #23, and not theoretical here
 * either: two products of the demo catalogue share a price, and names collide as easily.
 * {@code UPDATED_DESC} is the value where it matters most, since a bulk edit stamps several rows within
 * the same millisecond.
 */
public enum AdminProductSort {
    NAME_ASC(Sort.Direction.ASC, "name"),
    NAME_DESC(Sort.Direction.DESC, "name"),
    PRICE_ASC(Sort.Direction.ASC, "price"),
    PRICE_DESC(Sort.Direction.DESC, "price"),
    UPDATED_DESC(Sort.Direction.DESC, "updatedAt");

    /** What the contract declares as the default of the {@code AdminProductSort} schema. */
    public static final AdminProductSort DEFAULT = NAME_ASC;

    private static final String ID = "id";

    private final Sort.Direction direction;
    private final String property;

    AdminProductSort(Sort.Direction direction, String property) {
        this.direction = direction;
        this.property = property;
    }

    public Sort toSort() {
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, ID));
    }
}
