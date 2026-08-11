package dev.sandoretti.ukun.api.cart.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code PUT /api/cart/items/{productId}} — the {@code UpdateCartItemRequest} schema.
 *
 * <p><strong>{@code quantity} is the line's total, not an increment</strong>, which is what makes the
 * operation an idempotent upsert: adding a product and editing how many of it you want are the same
 * request, sent twice with the same body and leaving the same cart behind. The contract says it, RF-03
 * requires it — one line per {@code (customer, product)} — and calling it {@code quantity} rather than
 * {@code add} is the whole of the API's opinion on the matter.
 *
 * <p>{@code @Min(1)}, because {@code CHECK (quantity > 0)} in the database ({@code docs/10} §2) must
 * never be the thing that rejects a request: a constraint violation surfaces as a
 * {@code REFERENTIAL_INTEGRITY}-shaped accident, and the contract promises a {@code 400
 * VALIDATION_FAILED} naming the field. Removing a line is {@code DELETE}, not a quantity of zero.
 *
 * <p>{@code Integer} and {@code @NotNull} rather than {@code int}: an absent {@code quantity} would be
 * silently read as {@code 0} on a primitive and rejected by {@code @Min} with a message about a value
 * the caller never sent.
 */
public record UpdateCartItemRequest(@NotNull @Min(1) Integer quantity) {}
