package dev.sandoretti.ukun.api.stores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/**
 * A store, physical or online (RF-02). Owned by the {@code stores} module.
 *
 * <p><strong>{@code isOnline} is not resolved by name, ever.</strong> The legacy looks the online
 * store up with {@code tienda.nombre = 'Ukun Online'}, from inside a bean constructor, and fails to
 * start when that row is missing — defect #6 of {@code docs/03-analisis-y-patrones.md} §1. Here the
 * flag is a column, and "there is exactly one" is a database invariant rather than a convention:
 * {@code ux_store_online}, a partial unique index, rejects the second one
 * ({@code docs/10-modelo-de-datos.md} §2). Nothing in Java needs to re-check it.
 *
 * <p>No {@code @Data} (rule 1 of the skill {@code spring-api}): identity by id, {@code toString} over
 * scalars only, and no traversal into {@link StoreAddress}.
 *
 * <p>{@code created_at}/{@code updated_at} exist in the table with a database default and are
 * deliberately not mapped: no read exposes them, and {@code ddl-auto: validate} checks that the
 * entity's columns exist, not the other way round.
 */
@Entity
@Getter
@Setter
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique in the database ({@code ux_store_name}), which is what the listing sorts by. */
    @Column(nullable = false, length = 255)
    private String name;

    /** E.164, enforced by {@code ck_store_phone} — the legacy {@code VARCHAR(9)} could not hold one. */
    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "is_online", nullable = false)
    private boolean isOnline;

    /**
     * {@code @ManyToOne} and not {@code @OneToOne}, because {@code store.address_id} carries no unique
     * constraint: the schema does not stop two stores from pointing at one address row, and an
     * association that claims an invariant the database does not enforce is the same mistake the
     * legacy makes on {@code Producto.tipo} (defect #14).
     *
     * <p>{@code LAZY} explicitly — {@code @ManyToOne} defaults to {@code EAGER}, and the listing
     * fetches the address through an {@code @EntityGraph} instead, which is what keeps it at one
     * query (defect #12).
     *
     * <p>It stays a navigated association rather than a bare {@code Long}, unlike the six crossings of
     * ADR-0017 §2: this one does not leave the module.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "address_id", nullable = false)
    private StoreAddress address;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Store store)) {
            return false;
        }
        return id != null && id.equals(store.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /** No {@code address}: an association in a {@code toString} is how the legacy pair recursed. */
    @Override
    public String toString() {
        return "Store{id=%d, name='%s', isOnline=%s}".formatted(id, name, isOnline);
    }
}
