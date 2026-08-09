package dev.sandoretti.ukun.api.stores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The postal address of a store (RF-02). Owned by the {@code stores} module, which ADR-0017 §1 lists
 * as owning {@code store} and "the store's {@code address}".
 *
 * <p><strong>Why the class is not simply called {@code Address}.</strong> One table, {@code address},
 * holds two things that belong to two different modules: a store's address and a customer's profile
 * address ({@code customers}, v0.5). Hibernate keys entities by their <em>simple</em> class name, so
 * two {@code Address} classes in two packages would collide at boot with a duplicate mapping. Naming
 * this one after the side that owns it keeps the {@code customers} entity free to arrive later
 * without renaming anything here.
 *
 * <p>The {@code @Table} that follows from that is <strong>not</strong> the translation mapping the
 * hard rules ban. That rule is about {@code @Table(name = "producto")} — reaching back into a Spanish
 * legacy schema. This schema was born in English (ADR-0018); the annotation is here because the class
 * name is deliberately narrower than the table's, which is the opposite situation.
 *
 * <p>No {@code created_at}/{@code updated_at}: {@code docs/10-modelo-de-datos.md} §2 does not give
 * this table either column.
 */
@Entity
@Table(name = "address")
@Getter
@Setter
public class StoreAddress {

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
     * {@code CHAR(5)} in the schema, with {@code ck_address_postal_code} enforcing exactly five
     * digits — a Spanish postal code, as the contract's {@code Address} schema declares. It stays a
     * {@code String}: leading zeros are real (01001 is Vitoria) and an {@code int} would eat them.
     *
     * <p>{@code @JdbcTypeCode(CHAR)} because a {@code String} defaults to {@code VARCHAR}, and
     * {@code ddl-auto: validate} rejects the mismatch against the {@code bpchar} Postgres reports —
     * loudly, at startup, which is the whole point of validating. The padding {@code CHAR} normally
     * brings with it is not a concern here: the {@code CHECK} makes every value exactly five
     * characters long.
     */
    @Column(name = "postal_code", nullable = false, length = 5)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String postalCode;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StoreAddress address)) {
            return false;
        }
        return id != null && id.equals(address.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /** Scalars only, and no back-reference to the store — that is how {@code @Data} recursed. */
    @Override
    public String toString() {
        return "StoreAddress{id=%d, city='%s', postalCode='%s'}".formatted(id, city, postalCode);
    }
}
