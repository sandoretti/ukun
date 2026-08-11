package dev.sandoretti.ukun.api.orders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Where an order was sent, <strong>as it read the day it was placed</strong> (RF-04, ADR-0011).
 *
 * <p>This class is the fix for defect #19, the worst behavioural bug in the legacy system, and the
 * whole of the fix is that it is {@code @Embeddable} instead of an entity. The legacy order keeps a
 * foreign key to the customer's own {@code direccion} row, so editing a profile issues an
 * {@code UPDATE} against the row every past order points at: a customer who moves house rewrites the
 * shipping address of their entire history. Here the values are columns of {@code customer_order}.
 * There is no row for another feature to update, which makes the immutability structural rather than
 * a rule somebody has to keep remembering ({@code docs/10-modelo-de-datos.md} §5).
 *
 * <p><strong>Getters and no setters, and no setters is the point.</strong> The values are assigned
 * once, by the constructor, when the order is created. An order is a record of something that
 * happened; nothing in this service may edit it afterwards, and there is no method here through which
 * it could.
 *
 * <p>{@code @JdbcTypeCode(CHAR)} on the postal code for the reason {@code CustomerAddress} documents:
 * the column is {@code CHAR(5)}, a {@code String} maps to {@code VARCHAR} by default, and
 * {@code ddl-auto: validate} refuses to start on the mismatch.
 */
@Embeddable
@Getter
public class ShippingAddress {

    @Column(name = "shipping_street", nullable = false, updatable = false, length = 255)
    private String street;

    @Column(name = "shipping_city", nullable = false, updatable = false, length = 255)
    private String city;

    @Column(name = "shipping_province", nullable = false, updatable = false, length = 255)
    private String province;

    @Column(name = "shipping_postal_code", nullable = false, updatable = false, length = 5)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String postalCode;

    /** For JPA only. */
    protected ShippingAddress() {}

    public ShippingAddress(String street, String city, String province, String postalCode) {
        this.street = street;
        this.city = city;
        this.province = province;
        this.postalCode = postalCode;
    }

    /**
     * By value, unlike the entities around it, which compare by identifier. It has no identifier: two
     * orders sent to the same flat carry two copies of the same address and they are the same address.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ShippingAddress address)) {
            return false;
        }
        return Objects.equals(street, address.street)
                && Objects.equals(city, address.city)
                && Objects.equals(province, address.province)
                && Objects.equals(postalCode, address.postalCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(street, city, province, postalCode);
    }

    /**
     * City and province only. A full postal address is personal data, and this object exists inside an
     * order that gets logged by identifier all over the checkout path
     * ({@code docs/11-observabilidad-y-logging.md} §4).
     */
    @Override
    public String toString() {
        return "ShippingAddress[city=%s, province=%s]".formatted(city, province);
    }
}
