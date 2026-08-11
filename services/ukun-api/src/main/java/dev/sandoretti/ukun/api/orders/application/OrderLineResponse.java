package dev.sandoretti.ukun.api.orders.application;

import dev.sandoretti.ukun.api.shared.web.Money;
import java.math.BigDecimal;

/**
 * One line of an order — the {@code OrderLine} schema of the contract.
 *
 * <p>{@code productName} and {@code unitPrice} are snapshots taken at confirmation time (ADR-0011):
 * renaming, discontinuing or repricing the product does not alter any past order. {@code totalPrice}
 * always equals {@code unitPrice * quantity}, and the database guarantees it rather than this record
 * promising it — {@code ck_order_line_total}.
 *
 * <p>It carries the {@code productId}, unlike {@link CheckoutAttemptLineResponse} which carries the
 * SKU. The contract asks for both where it does for a reason: a finished order links to a product page
 * and needs the identifier, while an attempt is a receipt on screen.
 */
public record OrderLineResponse(
        Long productId, String productName, @Money BigDecimal unitPrice, int quantity, @Money BigDecimal totalPrice) {}
