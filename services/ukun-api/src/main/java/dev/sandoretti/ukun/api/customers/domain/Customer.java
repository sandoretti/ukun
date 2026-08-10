package dev.sandoretti.ukun.api.customers.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A customer's profile: who they are, where things are sent and how they pay (RF-05), created by the
 * signup saga (RF-06).
 *
 * <p><strong>{@code accountId} is not a foreign key and never can be.</strong> The account lives in
 * {@code ukun_auth}, a separate database, so the reference is an opaque UUID with a unique index
 * ({@code ux_customer_account_id}) and nothing more. That index is both the hottest query in the
 * system — every authenticated request resolves its customer through it — and the way ADR-0006 is
 * enforced in the database ({@code docs/08-identidad-y-seguridad.md} §1).
 *
 * <p>The legacy did the opposite: {@code cliente.id = cuenta.id} through {@code @MapsId}. That mapping
 * is what stops being expressible the moment identity moves to its own service, which is why
 * {@code customer.id} here is a key of its own.
 *
 * <p>{@code email} is a <strong>read projection</strong>, not the credential. The credential is in
 * ukun-auth; this copy exists so a listing does not have to call another service, and it is refreshed
 * from the token's claim on the owner's next request (§2 of the same document). Nothing here is keyed
 * by it.
 *
 * <p>{@code @Getter @Setter} and never {@code @Data}: the hard rule of {@code CLAUDE.md}, and this is
 * the entity where it earns its place twice over — a generated {@code toString} would print the card
 * and the address of every customer it touched.
 */
@Entity
@Table(name = "customer")
@Getter
@Setter
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The {@code sub} claim of the token, stored opaquely. See the class javadoc. */
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /** {@code CITEXT} in the schema, so comparisons are case-insensitive without a {@code lower()}. */
    @Column(nullable = false, columnDefinition = "citext")
    private String email;

    /**
     * The language this customer is written to in (ADR-0019). {@code CHAR(2)} with a {@code CHECK}
     * over {@code ('es', 'en')}, exposed and edited through {@code /api/me/profile}.
     *
     * <p>{@code insertable = false} and yet writable afterwards, which is not a contradiction: signup
     * does not ask for a language, so the {@code DEFAULT} of the column decides the initial value, and
     * from then on it is the customer's to change. The typed half of it is {@link ProfileLocale} —
     * this stays a {@code String} because the column holds {@code 'es'}, lowercase, and
     * {@code @Enumerated(STRING)} would write {@code 'ES'} straight into the {@code CHECK}.
     *
     * <p>{@code @JdbcTypeCode(CHAR)} for the reason {@code StoreAddress} documents about its postal
     * code: a {@code String} defaults to {@code VARCHAR}, and {@code ddl-auto: validate} refuses to
     * start against the {@code bpchar} Postgres reports. It caught exactly that here.
     */
    @Column(nullable = false, length = 2, insertable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String locale;

    /**
     * {@code CascadeType.ALL} and not merely {@code PERSIST}: the address belongs to this profile,
     * has no identity outside it and is never shared with another row. Signup writes the three
     * together in one transaction, and the cascade is what makes that one {@code save} rather than
     * three ordered ones.
     *
     * <p>Eager, unlike most associations here, and deliberately: every read of a profile renders the
     * address, so a lazy one would be the N+1 of defect #12 with an extra step. The listing that would
     * suffer from this does not exist — a customer only ever reads their own.
     */
    @OneToOne(cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "address_id", nullable = false)
    private CustomerAddress address;

    /** Same ownership and same reasoning as {@link #address}. */
    @OneToOne(cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private CustomerCard card;

    /**
     * Optional, and {@code ON DELETE SET NULL} in the schema: closing a store must not delete the
     * customers who liked it.
     *
     * <p>It is a {@code Long} and not a {@code Store}: an association would be a compile-time
     * dependency from {@code customers} to a {@code stores} entity, which is precisely what the module
     * boundary forbids ({@code docs/14-modules.md} §2). Its existence is checked through
     * {@code StoresApi} before the insert, and the foreign key is the second belt.
     */
    @Column(name = "favorite_store_id")
    private Long favoriteStoreId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false)
    private Instant updatedAt;

    @PreUpdate
    void stampUpdatedAt() {
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Customer customer)) {
            return false;
        }
        return id != null && id.equals(customer.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Customer.class);
    }

    /** No card, no address, no email: {@code docs/11-observabilidad-y-logging.md} §4. */
    @Override
    public String toString() {
        return "Customer[id=%s, accountId=%s]".formatted(id, accountId);
    }
}
