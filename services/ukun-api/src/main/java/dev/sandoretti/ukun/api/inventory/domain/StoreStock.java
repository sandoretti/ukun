package dev.sandoretti.ukun.api.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * How many units of a product a store holds. Owned by the {@code inventory} module.
 *
 * <p>Three decisions here come straight from the documents rather than from taste:
 *
 * <ul>
 *   <li><strong>A surrogate primary key</strong>, not the legacy composite {@code (producto_id,
 *       tienda_id)} (ADR-0015). The uniqueness invariant survives as {@code ux_store_stock}, so
 *       nothing is lost; what is gained is a line of stock that can be addressed by a URL, which
 *       {@code PUT /api/stores/{storeId}/stock/{lineId}} needs.
 *   <li><strong>{@code productId} and {@code storeId} are bare {@code Long}s</strong>, not
 *       {@code @ManyToOne}. Both cross a module boundary, and ADR-0017 §2 lists these two among the
 *       six associations that become identifiers. The foreign keys stay in the database untouched;
 *       what disappears is the navigation in Java. The conversion costs nothing here — the legacy
 *       already maps both {@code insertable = false, updatable = false} as part of its composite key.
 *   <li><strong>{@code @Version}</strong>, because editing stock by hand is optimistic (ADR-0008) —
 *       unlike {@code product}, which is last-write-wins on purpose ({@code docs/10} §2). The read in
 *       this release does not use it; the column exists and the mapping is where it belongs.
 * </ul>
 *
 * <p>{@code @Table} for the same reason {@code StoreAddress} carries one: Hibernate's default strategy
 * would map {@code StoreStock} onto {@code store_stock} correctly, but being explicit on a table whose
 * name is also its module's central concept is worth the line. Nothing here is a translation of a
 * Spanish name — this schema was born in English (ADR-0018).
 */
@Entity
@Table(name = "store_stock")
@Getter
@Setter
public class StoreStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    /**
     * {@code CHECK (quantity >= 0)} in the database — the legacy declares {@code @Min(0)} and lets its
     * schema allow anything.
     */
    @Column(nullable = false)
    private int quantity;

    @Version
    @Column(nullable = false)
    private long version;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StoreStock stock)) {
            return false;
        }
        return id != null && id.equals(stock.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "StoreStock{id=%d, productId=%d, storeId=%d, quantity=%d}".formatted(id, productId, storeId, quantity);
    }
}
