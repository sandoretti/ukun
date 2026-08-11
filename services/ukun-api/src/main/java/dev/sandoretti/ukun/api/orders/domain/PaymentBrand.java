package dev.sandoretti.ukun.api.orders.domain;

import java.util.Locale;

/**
 * The card network an order was paid with, frozen at confirmation time (ADR-0011).
 *
 * <p>Four values, the same four {@code customers.domain.CardBrand} holds and the same four
 * {@code ck_customer_order_payment_brand} accepts. Two enums rather than one shared type is a decision
 * {@link dev.sandoretti.ukun.api.customers.CustomerSnapshot} argues in full: the alternative is a
 * module publishing a type out of its {@code domain} package, which {@code docs/14-modules.md} §2 does
 * not allow.
 *
 * <p>{@link #OTHER} is not a fallback for "we could not parse it" — see {@link #of(String)}. It is a
 * card network the shop does not name, which is a different thing from a value nobody recognises.
 */
public enum PaymentBrand {
    VISA,
    MASTERCARD,
    AMEX,
    OTHER;

    /**
     * The brand behind the text {@code customers} handed over.
     *
     * <p>It throws rather than falling back to {@link #OTHER}. The value comes from a column with a
     * {@code CHECK} over these four words, so anything else means the two tables have drifted apart —
     * and quietly recording somebody's Visa as {@code OTHER} would hide that in the one place nobody
     * re-reads, the payment snapshot of a finished order.
     */
    public static PaymentBrand of(String name) {
        return valueOf(name.toUpperCase(Locale.ROOT));
    }
}
