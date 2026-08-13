package dev.sandoretti.ukun.api.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * A catalog product (RF-01, RF-10). Owned by the {@code catalog} module.
 *
 * <p>Four decisions here are corrections of named legacy defects, not style:
 *
 * <ul>
 *   <li><strong>No {@code @Data}</strong> (rule 1 of the skill {@code spring-api}). It generates
 *       {@code equals}/{@code hashCode}/{@code toString} over every field, associations included —
 *       the direct cause of a real {@code StackOverflowError} between {@code Pedido} and
 *       {@code ProductoPedido}, and of secrets reaching the log through a generated
 *       {@code toString} ({@code docs/11-observabilidad-y-logging.md} §4).
 *   <li><strong>{@code price} is {@code BigDecimal}</strong>, never {@code Float} (defect #5): the
 *       legacy maps {@code DECIMAL(10,2)} onto {@code Float} and genuinely loses cents.
 *   <li><strong>{@code productType} is {@code @ManyToOne(fetch = LAZY)}, explicitly</strong>
 *       (defects #12 and #14). The legacy declares this association {@code @OneToOne}, which is
 *       both the wrong cardinality and EAGER by default. The listing then relies on
 *       {@code @EntityGraph} to fetch it in the same query.
 *   <li><strong>No {@code @Version}</strong>. Editing a product is last-write-wins, decided in
 *       {@code docs/10-modelo-de-datos.md} §2 — not an omission, and not to be "made consistent"
 *       with {@code store_stock}, which has a different problem.
 * </ul>
 *
 * <p>{@code productType} is a navigated association and not a bare {@code Long} because
 * {@code product_type} belongs to this same module; {@code docs/10} §5 reserves the bare identifier
 * for the six associations that cross a module boundary.
 *
 * <p>{@code created_at}/{@code updated_at} are mapped since v0.7, when {@code AdminProduct} became the
 * first response that exposes them (RF-10). The public reads still do not carry them.
 */
@Entity
@Getter
@Setter
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Business identifier, typed in by whoever creates the product and immutable afterwards (ADR-0015). */
    @Column(nullable = false, length = 32)
    private String sku;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_type_id", nullable = false)
    private ProductType productType;

    /**
     * Storage key, never exposed: the API composes a servable {@code imageUrl} from it
     * ({@code docs/08-identidad-y-seguridad.md} §9). Nullable — the photo is optional (RF-10).
     */
    @Column(name = "image_key", length = 255)
    private String imageKey;

    /**
     * The only soft deletion in this schema (ADR-0013). With {@code RESTRICT} on every inbound
     * foreign key, a product that has ever been sold can never be deleted, so discontinuing it is
     * the real business exit. The public catalog returns active products only.
     */
    @Column(nullable = false)
    private boolean isActive = true;

    /**
     * Written by the database default and read back after the insert, which is what {@code @Generated}
     * buys: {@code AdminProduct} declares {@code createdAt} required, so a {@code 201} that answered
     * with {@code null} because the row was never re-read would break the contract on the very
     * operation that creates it.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /**
     * Same on insert, stamped here on update: the column has a database default, and a default only
     * fires once. Postgres would need a trigger to maintain it, and this schema has none by choice
     * ({@code docs/10-modelo-de-datos.md} §1) — so the entity keeps it, exactly as {@code Customer}
     * does.
     */
    @Generated(event = EventType.INSERT)
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
        if (!(other instanceof Product product)) {
            return false;
        }
        return id != null && id.equals(product.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /**
     * Scalars only. No {@code description} (long and useless in a log line) and, above all, no
     * {@code productType}: an association in a {@code toString} is how the legacy pair recursed
     * until the stack ran out.
     */
    @Override
    public String toString() {
        return "Product{id=%d, sku='%s', name='%s', price=%s}".formatted(id, sku, name, price);
    }
}
