package dev.sandoretti.ukun.api.orders.application;

import dev.sandoretti.ukun.api.shared.error.ErrorCode;
import dev.sandoretti.ukun.api.shared.error.UkunException;

/**
 * {@code EMPTY_CART} (422): there is nothing to buy.
 *
 * <p>RF-04 lists this under "do not carry over": the legacy happily creates an order with no lines and
 * a total of zero, which is a bug rather than a feature — an order nobody can fulfil, in a history
 * nobody can read. It is raised twice on this path, when the checkout is opened and again when it is
 * confirmed, because the cart can be emptied in another tab in between.
 *
 * <p>No extensions. The frontend needs no data to say "your cart is empty", and the customer is
 * looking at the screen that already shows it.
 */
public final class EmptyCartException extends UkunException {

    public EmptyCartException() {
        super("The cart is empty");
    }

    @Override
    public ErrorCode getCode() {
        return ErrorCode.EMPTY_CART;
    }
}
