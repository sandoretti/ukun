package dev.sandoretti.ukun.api.inventory;

import java.util.List;

/**
 * What happened when checkout asked the online store for the units of an order: either every line was
 * taken, or one of them could not be and nothing was taken at all.
 *
 * <p><strong>A result, not an exception.</strong> Running out of stock is an ordinary outcome of
 * buying things, and the module that owns {@code store_stock} is not the module that knows what the
 * REST contract answers to it — the {@code sku} the {@code INSUFFICIENT_STOCK} extension carries is
 * not even a field this module reads ({@code docs/12-error-contract.md}). {@code orders} turns
 * {@link Rejected} into the error; {@code inventory} states the fact.
 *
 * <p><strong>Sealed, so the caller cannot forget the unhappy path.</strong> A method returning
 * {@code boolean} would put "did it work?" one ignored return value away from selling stock the shop
 * does not have, which is defect #2 with better manners.
 *
 * <p>Neither branch carries a {@code StoreStock}: the golden rule of {@code docs/14-modules.md} §2,
 * and the reason {@link Rejected} names the line by its identifier rather than handing over the row.
 */
public sealed interface StockDecrement {

    /**
     * Every line was decremented. Carries what changed so the caller can emit
     * {@code stock.decremented.checkout} ({@code docs/11-observabilidad-y-logging.md} §3) with the
     * before and after the registry asks for — this module cannot emit it itself, because the event
     * is keyed by an {@code orderId} it has never heard of.
     */
    record Applied(List<StockChange> changes) implements StockDecrement {}

    /**
     * One line could not be served, and therefore none of them was: RF-04 has no partial orders and no
     * backorders. It names the first line that came up short in the order the request listed them, so
     * the same cart always reports the same product rather than whichever one the database happened to
     * return first.
     *
     * <p>{@code stockLineId} is {@code null} when the product has no stock row in the online store at
     * all. That is a different fact from a row sitting at zero — the row can be topped up, the absence
     * has to be created — and it is the one place in this service where the difference is visible,
     * because everywhere else RF-01 requires that nothing outside can tell them apart.
     */
    record Rejected(Long stockLineId, Long productId, int available, int requested) implements StockDecrement {}

    /** One line's movement: what it held, and what it holds now. */
    record StockChange(Long productId, int before, int after) {}
}
