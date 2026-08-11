package dev.sandoretti.ukun.api.orders.application;

import dev.sandoretti.ukun.api.shared.web.Money;
import java.math.BigDecimal;

/**
 * One line of a checkout attempt — the {@code CheckoutAttemptLine} schema of the contract.
 *
 * <p><strong>{@code unitPrice} is the price frozen when the attempt was opened</strong>, unlike the
 * cart's, which is whatever the product costs at the moment of reading. That difference is the whole
 * purpose of the attempt: the amount on the confirmation screen is the amount that will be charged,
 * and it stops being a promise the moment somebody re-reads the catalogue instead of this row.
 *
 * <p><strong>{@code available} is information and nothing more.</strong> It reserves no stock —
 * ADR-0016 rules a TTL reservation out by name — so it may be gone by the time the customer confirms.
 * What it buys is that {@code 409 INSUFFICIENT_STOCK} becomes the rare case of a real collision rather
 * than the first the customer hears about a product being out of stock.
 *
 * <p>It carries the {@code sku} and not the {@code productId}: the contract says so, and it is the
 * right call — this is a receipt-shaped object, and a SKU is what the shop and the customer both call
 * the thing. The identifier appears where a machine needs it, in the {@code INSUFFICIENT_STOCK}
 * extensions.
 */
public record CheckoutAttemptLineResponse(
        String sku, String productName, @Money BigDecimal unitPrice, int quantity, int available) {}
