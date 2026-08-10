package dev.sandoretti.ukun.api.customers.domain;

/**
 * The card networks the contract's {@code Card} schema declares, and the closed list
 * {@code ck_card_brand} enforces in the database.
 *
 * <p>A {@code VARCHAR} plus a {@code CHECK} rather than a native Postgres enum type, for the reason
 * ADR-0012 gives about the order status: adding a value later is an {@code ALTER} of one constraint
 * instead of an {@code ALTER TYPE}. {@code OTHER} is what keeps that day from arriving for every new
 * network.
 */
public enum CardBrand {
    VISA,
    MASTERCARD,
    AMEX,
    OTHER
}
