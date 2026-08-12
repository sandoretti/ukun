package dev.sandoretti.ukun.api.inventory;

import dev.sandoretti.ukun.api.catalog.ProductSummary;
import java.util.Map;

/**
 * What the {@code inventory} module offers the rest of the application: how many units of a product
 * the shop can actually sell right now. The only type here other modules may import
 * ({@code docs/14-modules.md} §1).
 *
 * <p>It returns no {@code StoreStock} and takes none — the golden rule of §2 of that document. What
 * crosses the boundary is a number, which is all a cart line or a checkout attempt has ever needed.
 *
 * <p><strong>Both methods take the products rather than the bare identifiers</strong>, and that is the
 * whole of issue #122. "Can sell" is not "has units in the warehouse": a discontinued product keeps its
 * stock row and stops being sellable (ADR-0013), and this module is the one that gets to say so. The
 * callers used to correct the answer themselves — the same expression copied into {@code cart} and
 * {@code orders} — which put a rule of {@code inventory} in two modules that do not own it.
 *
 * <p><strong>Why the catalogue is not read here instead.</strong> Every caller has already read the
 * same batch through {@code CatalogApi} before it gets this far: it needs the name and the price to
 * show the line at all. Resolving it again inside this module would issue the identical query a second
 * time in the same transaction — a statement per cart read and per checkout attempt, paid to avoid
 * passing an argument. The map handed over is the one the caller is about to render, so the two cannot
 * disagree.
 */
public interface InventoryApi {

    /**
     * Units of each product the online store can actually sell, keyed by product identifier.
     *
     * <p><strong>A product that is not sellable is absent from the answer</strong>, and a discontinued
     * one is not sellable however many units its row holds (ADR-0013). The caller reads absence as zero,
     * which is what it already does for a product with no stock row at all.
     *
     * <p><strong>Why the online store is not a parameter.</strong> The caller would have to know what
     * an online store is, and {@code cart} may not: {@code stores} is not among its allowed
     * dependencies (ADR-0017 §1). This module already depends on {@code stores} for the availability
     * endpoint, so resolving it here is the only place where the rule of RF-04 — the shop sells from the
     * online store and from nowhere else — stays in one piece.
     *
     * <p>A product with no stock row is <strong>absent from the map</strong>, exactly as it is absent
     * from {@code CatalogApi}: the caller reads it as zero, which is the same answer RF-01 gives for the
     * public endpoint. What must not happen is for "no row" and "a row at zero" to look different from
     * outside, and here they cannot — both come back as no units.
     *
     * <p><strong>This is the one place a quantity leaves the module.</strong> The public
     * {@code GET /api/availability} answers a boolean on purpose, because a number would tell anybody
     * with a browser how much stock the shop holds; the contract only ever shows the figure to the
     * customer looking at their own cart or checkout attempt. Any new caller of this method is a
     * decision about that, not a detail.
     */
    Map<Long, Integer> sellableOnlineQuantities(Map<Long, ProductSummary> products);

    /**
     * Takes the units of an order out of the online store, all of them or none.
     *
     * <p><strong>This is where defect #2 is fixed.</strong> The legacy reads the stock, subtracts in
     * Java and writes it back with nothing holding the row in between, so two customers buying the
     * last unit at the same time both get it. Here the rows are locked with {@code SELECT … FOR
     * UPDATE} <strong>ordered by {@code store_stock.id}</strong> before anything is read: the lock is
     * what makes the check-then-write atomic, and the ordering is what stops two orders sharing
     * products in different sequences from deadlocking each other (ADR-0008, with the ordering
     * criterion updated to the surrogate key by ADR-0015).
     *
     * <p><strong>The online store is not a parameter here either</strong>, for the reason the method
     * above gives at length: RF-04 makes it a business rule that the shop sells from the online store
     * and from nowhere else, and this module is the one place that gets to resolve it.
     *
     * <p>The quantities arrive keyed by product and are read <strong>in the map's iteration
     * order</strong>, which is why the caller passes an ordered map: it decides which line a
     * {@link StockDecrement.Rejected} names when more than one is short.
     *
     * <p><strong>A product that is not sellable is rejected as having none available</strong>, whatever
     * its stock row says, and before any row is locked. Discontinuing takes a product out of the
     * catalogue without emptying the warehouse (ADR-0013), so the units really are there — and selling
     * them anyway would make "discontinued" mean nothing to the one operation that matters. The cart and
     * the checkout attempt already report zero for it through the method above, so the customer sees the
     * same number before and after pressing the button. A product deleted outright lands here too, and
     * answers identically.
     *
     * <p>It does not throw when there is not enough — see {@link StockDecrement}. What it does not do
     * either is reserve, hold or expire anything: ADR-0016 rules a TTL reservation out by name, so
     * the units are taken at confirmation time and never a moment earlier.
     */
    StockDecrement decrementOnlineStock(
            Map<Long, Integer> quantitiesByProductId, Map<Long, ProductSummary> products);
}
