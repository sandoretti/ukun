package dev.sandoretti.ukun.api.cart.application;

import dev.sandoretti.ukun.api.shared.web.Money;
import java.math.BigDecimal;
import java.util.List;

/**
 * The whole cart — the {@code Cart} schema of the contract, and the body of all three operations that
 * return one.
 *
 * <p><strong>An empty cart is this object with an empty list and a total of zero, never a
 * {@code 404}.</strong> The contract says so in as many words, and it is the difference between "you
 * have nothing in your cart" and "you have no cart", which is not a distinction a shopper has any use
 * for.
 *
 * <p>{@code total} is computed over the lines on the way out rather than stored: a sum kept in a column
 * is a second source of truth that goes stale the first time a price changes, and the cart re-reads
 * prices on every request by design.
 */
public record CartResponse(List<CartLineResponse> items, @Money BigDecimal total) {}
