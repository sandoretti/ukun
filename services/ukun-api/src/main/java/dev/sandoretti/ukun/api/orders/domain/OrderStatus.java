package dev.sandoretti.ukun.api.orders.domain;

/**
 * Where an order is in its life. Today there is one place to be.
 *
 * <p>The column already accepts {@code 'CANCELLED'} too — {@code ck_customer_order_status}, ADR-0012 —
 * and this enum deliberately does not. The contract's {@code OrderStatus} publishes exactly one value,
 * frozen at SINC-1, and a constant Java can produce but the API does not declare is a response the
 * frontend has no type for. The database reserves the room; the code adds the value when there is an
 * operation that reaches it, which the roadmap places in v1.1 alongside a real payment gateway.
 *
 * <p>{@code @Enumerated(EnumType.STRING)} everywhere it is mapped, never {@code ORDINAL}: the column
 * holds the word, so a value inserted here reads the same from {@code psql} as it does from Java, and
 * reordering this enum cannot silently rewrite history (ADR-0012).
 */
public enum OrderStatus {
    CONFIRMED
}
