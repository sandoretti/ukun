package dev.sandoretti.ukun.api.orders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * How an order was paid, <strong>as it read the day it was placed</strong> (RF-04, ADR-0011). The
 * payment half of what {@link ShippingAddress} does for the address, and it is the same defect #19:
 * the legacy order shares its {@code tarjeta} row with the live profile, so replacing an expired card
 * rewrites the payment details of every order the customer ever made.
 *
 * <p><strong>Four fields, and the two that matter are the ones that are missing.</strong> There is no
 * column for the card number and none for the CVV, here or anywhere else in this service. Storing a
 * CVV is forbidden by PCI-DSS under any circumstance, encryption included, and the legacy
 * {@code tarjeta} table holds both in the clear (defect #15, {@code docs/08-identidad-y-seguridad.md}
 * §5). The CVV does not travel in the checkout request either — the contract says so where
 * {@code POST /api/orders} is declared — so there is nothing to leave out.
 *
 * <p>Immutable for the same reason as the address: assigned by the constructor, no setters, columns
 * declared {@code updatable = false}.
 */
@Embeddable
@Getter
public class PaymentSnapshot {

    /** {@code CHAR(4)} with a {@code CHECK} for four digits; a {@code String} because {@code 0042} is not 42. */
    @Column(name = "payment_last4", nullable = false, updatable = false, length = 4)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String last4;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_brand", nullable = false, updatable = false, length = 20)
    private PaymentBrand brand;

    /**
     * Month and year rather than a date, matching {@code card} and for the reason {@code docs/10} §2
     * gives: a card is valid through a whole month, and the legacy {@code '2025-12-01'} literally reads
     * "expired on 1 December".
     */
    @Column(name = "payment_exp_month", nullable = false, updatable = false)
    private short expMonth;

    @Column(name = "payment_exp_year", nullable = false, updatable = false)
    private short expYear;

    /** For JPA only. */
    protected PaymentSnapshot() {}

    public PaymentSnapshot(String last4, PaymentBrand brand, short expMonth, short expYear) {
        this.last4 = last4;
        this.brand = brand;
        this.expMonth = expMonth;
        this.expYear = expYear;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PaymentSnapshot payment)) {
            return false;
        }
        return expMonth == payment.expMonth
                && expYear == payment.expYear
                && Objects.equals(last4, payment.last4)
                && brand == payment.brand;
    }

    @Override
    public int hashCode() {
        return Objects.hash(last4, brand, expMonth, expYear);
    }

    /** Safe by construction — there is no PAN and no CVV on this class to print. */
    @Override
    public String toString() {
        return "PaymentSnapshot[brand=%s, last4=%s]".formatted(brand, last4);
    }
}
