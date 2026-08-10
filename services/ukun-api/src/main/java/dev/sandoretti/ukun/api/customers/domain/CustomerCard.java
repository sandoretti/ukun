package dev.sandoretti.ukun.api.customers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A customer's payment method, <strong>masked</strong>.
 *
 * <p>There is no field for the full number and none for the CVV, and their absence is the point.
 * Storing the CVV is forbidden by PCI-DSS under any circumstance, encrypted included, and the legacy
 * {@code tarjeta} table stores both in the clear (defect #15, {@code docs/08-identidad-y-seguridad.md}
 * §5). The fix is not encryption or redaction — it is that the columns do not exist, so no code path
 * can put them there.
 *
 * <p>Expiry is a month and a year rather than a date: a card is valid through a whole month, and the
 * legacy {@code '2025-12-01'} literally reads "expired on 1 December" ({@code docs/10} §2).
 *
 * <p>v1.x replaces this with a token from a real payment gateway, at which point this service stops
 * touching card data in any form at all (issue #60).
 */
@Entity
@Table(name = "card")
@Getter
@Setter
public class CustomerCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The last four digits, which is all that is kept and all a customer needs to recognise their own
     * card. A {@code String} for the same reason as the postal code: {@code 0042} is four digits, not
     * the number forty-two.
     */
    @Column(nullable = false, length = 4)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String last4;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardBrand brand;

    @Column(name = "exp_month", nullable = false)
    private short expMonth;

    @Column(name = "exp_year", nullable = false)
    private short expYear;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CustomerCard card)) {
            return false;
        }
        return id != null && id.equals(card.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(CustomerCard.class);
    }

    /**
     * Safe by construction, because there is nothing unsafe to print: no PAN and no CVV exist on this
     * class. It is written out anyway rather than left to Lombok, per the hard rule of
     * {@code CLAUDE.md} — {@code @Data} on the legacy entity is exactly how the PAN and the CVV ended
     * up in a log line (defects #20a and #20b).
     */
    @Override
    public String toString() {
        return "CustomerCard[id=%s, brand=%s, last4=%s]".formatted(id, brand, last4);
    }
}
