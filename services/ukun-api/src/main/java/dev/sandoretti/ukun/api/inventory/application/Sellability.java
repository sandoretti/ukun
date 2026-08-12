package dev.sandoretti.ukun.api.inventory.application;

import dev.sandoretti.ukun.api.catalog.ProductSummary;

/**
 * The one place that decides whether a product has anything to sell.
 *
 * <p><strong>Discontinuing takes a product out of the catalogue; it does not empty the warehouse</strong>
 * (ADR-0013). Its {@code store_stock} row may well hold units, and they really are there — what changed
 * is that the shop no longer sells them. So a discontinued product reports zero available, whatever the
 * row says, and an order for it is refused for the same reason.
 *
 * <p><strong>Why it lives here and not at the three call sites it used to</strong> (issue #122).
 * Availability belongs to {@code inventory}, and until now {@code cart} and {@code orders} each applied
 * this rule on their own, with the same expression copied. They agreed, which is exactly what made it
 * easy to leave alone: the day availability depends on something more — a reservation, a safety
 * threshold — three places would have to be remembered and nothing would insist. Now the callers hand
 * over what they read from {@code CatalogApi} and this module answers; they derive nothing.
 *
 * <p>A product absent from the batch is not sellable either. Callers reach this method with identifiers
 * taken from their own rows — a cart line, a frozen checkout line — and an identifier the catalogue does
 * not know about is one whose product was deleted outright. There is nothing to sell either way, and
 * telling the two apart is a distinction only {@code catalog} is allowed to make.
 */
final class Sellability {

    private Sellability() {}

    static boolean sellable(ProductSummary product) {
        return product != null && product.active();
    }
}
