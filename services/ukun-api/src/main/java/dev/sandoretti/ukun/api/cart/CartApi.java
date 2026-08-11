package dev.sandoretti.ukun.api.cart;

import java.util.List;

/**
 * What the {@code cart} module offers the rest of the application. Its only caller is {@code orders}:
 * checkout reads the cart it is about to turn into an order, and empties it when the order is placed
 * — "the cart is emptied automatically when an order is completed" (RF-03), which is a rule of the
 * cart and therefore implemented here rather than by {@code orders} deleting rows it does not own.
 *
 * <p>Both methods take the {@code customerId} instead of resolving it from the token, and that is the
 * one place in this module where they do. Checkout runs as one transaction over several aggregates and
 * resolves the customer once; re-reading the token per module call would be the same query three times
 * and, worse, would make each module's answer depend on a security context it should not be reading.
 * The value still comes from {@code CustomersApi} and never from the request
 * ({@code docs/08-identidad-y-seguridad.md} §4).
 */
public interface CartApi {

    /** The lines of a customer's cart, in a stable order. An empty cart is an empty list. */
    List<CartLineSnapshot> linesOf(Long customerId);

    /**
     * Removes every line of a customer's cart. Idempotent: emptying an already empty cart is not an
     * error, here for the same reason {@code DELETE /api/cart} answers {@code 204} either way.
     */
    void clear(Long customerId);
}
