package dev.sandoretti.ukun.api.customers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * The postal address of a customer's profile (RF-05, RF-06). Owned by {@code customers}, which
 * ADR-0017 §1 lists as owning {@code customer} and "the profile's {@code address} and {@code card}".
 *
 * <p><strong>Why not simply {@code Address}.</strong> One table, {@code address}, serves two modules:
 * a store's address and a customer's. Hibernate keys entities by their <em>simple</em> class name, so
 * two classes called {@code Address} in two packages collide at boot with a duplicate mapping — which
 * is why {@code stores.domain.StoreAddress} was already named after its owner, anticipating this one.
 * The {@code @Table} that follows is not the translation mapping the hard rules ban: that rule is
 * about reaching back into a Spanish legacy schema, and this schema was born in English (ADR-0018).
 *
 * <p>ADR-0017 §1 also explains why {@code address} is not a module of its own: it is data with no
 * behaviour, belonging to whoever owns the row. A module {@code addresses} would be a shared table
 * dressed up as a domain boundary.
 *
 * <p>The address of an <em>order</em> is a different thing again — copied into {@code customer_order}
 * as immutable columns, never referenced (ADR-0011). Editing this row must not rewrite the shipping
 * address of a past order, which is defect #19 and the worst behavioural bug in the legacy.
 */
@Entity
@Table(name = "address")
@Getter
@Setter
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String street;

    @Column(nullable = false, length = 255)
    private String city;

    @Column(nullable = false, length = 255)
    private String province;

    /**
     * {@code CHAR(5)}, with {@code ck_address_postal_code} enforcing exactly five digits. It stays a
     * {@code String} because leading zeros are real — 01001 is Vitoria — and an {@code int} eats them.
     *
     * <p>{@code @JdbcTypeCode(CHAR)} because a {@code String} defaults to {@code VARCHAR} and
     * {@code ddl-auto: validate} rejects the mismatch against the {@code bpchar} Postgres reports.
     */
    @Column(name = "postal_code", nullable = false, length = 5)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String postalCode;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CustomerAddress address)) {
            return false;
        }
        return id != null && id.equals(address.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(CustomerAddress.class);
    }

    @Override
    public String toString() {
        return "CustomerAddress[id=%s, city=%s]".formatted(id, city);
    }
}
