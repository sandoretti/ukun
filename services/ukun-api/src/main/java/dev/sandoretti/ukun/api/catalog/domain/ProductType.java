package dev.sandoretti.ukun.api.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * A catalog category (RF-01, RF-13). Owned by the {@code catalog} module, like {@code product}.
 *
 * <p>Its name is unique in the database ({@code ux_product_type_name}) and editable: renaming a type
 * drags nothing behind it, because what an order line freezes is the <em>product</em> name, not its
 * type ({@code docs/10-modelo-de-datos.md} §5).
 *
 * <p>No {@code @Table}/{@code @Column} renaming anywhere: the schema was born in English (ADR-0018)
 * and the default naming strategy already maps {@code ProductType} onto {@code product_type}. If you
 * find yourself writing one, you have copied from an outdated source.
 *
 * <p>{@code created_at}/{@code updated_at} are mapped since v0.7, when {@code AdminProductType} became
 * the first response that exposes them (RF-13). The same pair, mapped the same way and for the same
 * reasons, as {@code Product} — see its javadoc.
 */
@Entity
@Getter
@Setter
public class ProductType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    /** Database default, read back after the insert. See {@code Product#getCreatedAt()}. */
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** Database default on insert, stamped by {@link #stampUpdatedAt()} on every rename. */
    @Generated(event = EventType.INSERT)
    @Column(name = "updated_at", insertable = false)
    private Instant updatedAt;

    @PreUpdate
    void stampUpdatedAt() {
        this.updatedAt = Instant.now();
    }

    /**
     * By id, and only once the row has one: two unsaved instances are never the same type, which is
     * what stops a {@code HashSet} of new entities from collapsing into one element.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProductType productType)) {
            return false;
        }
        return id != null && id.equals(productType.id);
    }

    /**
     * Constant per class, on purpose. A hash derived from the id would change the moment the entity
     * is persisted and the instance would be lost inside any hash-based collection it had joined.
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /** Scalars only — never an association, which is how {@code @Data} caused a real StackOverflowError. */
    @Override
    public String toString() {
        return "ProductType{id=%d, name='%s'}".formatted(id, name);
    }
}
