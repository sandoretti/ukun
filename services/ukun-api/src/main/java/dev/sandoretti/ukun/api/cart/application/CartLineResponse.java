package dev.sandoretti.ukun.api.cart.application;

import dev.sandoretti.ukun.api.shared.web.Money;
import java.math.BigDecimal;

/**
 * One line of the cart — the {@code CartLine} schema of the contract.
 *
 * <p>Three sources meet here and only one of them is this module's: the quantity is the
 * {@code cart_item} row, the sku, the name and the price come from {@code CatalogApi}, and
 * {@code available} from {@code InventoryApi}. That is why the object is built in
 * {@link CartService} instead of by a MapStruct mapper — it is a composition, not the projection of an
 * entity, the same reason {@code AvailabilityQueryService} builds its own items. Rule 3 of the skill
 * {@code spring-api} still applies where it is about: {@code Product} becomes a {@code ProductSummary}
 * through {@code ProductMapper}, in the module that owns it.
 *
 * <p>{@code unitPrice} is what the product costs <strong>now</strong>, re-read on every request. It is
 * not a promise: the price a customer pays is frozen when the order is placed (RF-04), and a cart left
 * open for a week will show the new price the moment it changes.
 *
 * <p>{@code available} is the units the online store holds. It is informational — it reserves nothing
 * and may be gone by the time the order is confirmed — and it exists because RF-03 marks its absence as
 * a gap not to carry over: the legacy lets you put 999 units of a product with a stock of 3 into the
 * cart and says nothing until checkout.
 */
public record CartLineResponse(
        Long productId,
        String sku,
        String productName,
        @Money BigDecimal unitPrice,
        int quantity,
        @Money BigDecimal lineTotal,
        int available) {}
