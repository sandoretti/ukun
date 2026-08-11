package dev.sandoretti.ukun.api.inventory;

import dev.sandoretti.ukun.api.inventory.domain.StoreStock;

/**
 * Stock lines, per the {@code testing} skill: built explicitly, so a fixture says what situation it is
 * setting up instead of listing three column values.
 *
 * <p>Public because stock is not only {@code inventory}'s business — the cart reports it and checkout
 * will decrement it, and each of those suites writing its own three-setter helper is how three copies
 * of the same row start drifting.
 *
 * <p>It takes identifiers rather than a {@code Product} and a {@code Store}, which is not a shortcut:
 * {@code StoreStock} holds bare {@code Long}s because both associations cross a module boundary
 * (ADR-0017 §2), and a mother that took entities would suggest a navigation the entity does not have.
 */
public final class StoreStockMother {

    private StoreStockMother() {}

    /** A line with units in it. */
    public static StoreStock stock(long productId, long storeId, int quantity) {
        StoreStock stock = new StoreStock();
        stock.setProductId(productId);
        stock.setStoreId(storeId);
        stock.setQuantity(quantity);

        return stock;
    }

    /**
     * A line sitting at zero — a distinct case from having no line at all, and the whole point of the
     * pair: RF-01 requires that nothing outside can tell them apart.
     */
    public static StoreStock outOfStock(long productId, long storeId) {
        return stock(productId, storeId, 0);
    }
}
