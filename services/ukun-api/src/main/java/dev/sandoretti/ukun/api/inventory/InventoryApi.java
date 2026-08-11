package dev.sandoretti.ukun.api.inventory;

import java.util.Map;
import java.util.Set;

/**
 * What the {@code inventory} module offers the rest of the application: how many units of a product
 * the shop can actually sell right now. The only type here other modules may import
 * ({@code docs/14-modules.md} §1).
 *
 * <p>It returns no {@code StoreStock} and takes none — the golden rule of §2 of that document. What
 * crosses the boundary is a number, which is all a cart line or a checkout attempt has ever needed.
 */
public interface InventoryApi {

    /**
     * Units of each product held by the online store, keyed by product identifier.
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
    Map<Long, Integer> onlineQuantitiesByProductIds(Set<Long> productIds);
}
