package dev.sandoretti.ukun.api.cart.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A line of somebody's cart: a product and how many of it (RF-03). Owned by the {@code cart} module.
 *
 * <p><strong>It holds no price.</strong> The cart shows what the product costs <em>today</em>, read
 * from {@code catalog} on every request; the price is frozen when the order is placed and not a
 * moment earlier (RF-04, ADR-0011). A {@code unit_price} column here would be a second copy of the
 * catalogue that nobody updates, and the customer would pay yesterday's price or see a total that does
 * not match the shop.
 *
 * <p><strong>{@code customerId} and {@code productId} are bare {@code Long}s</strong> inside
 * {@link CartItemId}, not {@code @ManyToOne}. Both cross a module boundary and ADR-0017 §2 lists them
 * among the six associations that become identifiers: the foreign keys stay in the database, the
 * navigation disappears from Java. That is also why this class has no {@code Product} field for the
 * name the response carries — it comes from {@code CatalogApi}, in one batch call for the whole cart.
 *
 * <p>No {@code created_at}/{@code updated_at}: a cart is ephemeral state, which is exactly why
 * {@code docs/10-modelo-de-datos.md} §1 leaves this table out of the timestamped ones.
 *
 * <p>{@code @Getter @Setter} and never {@code @Data}, with {@code equals}, {@code hashCode} and
 * {@code toString} written by hand — the hard rule of {@code CLAUDE.md}, born of a real
 * {@code StackOverflowError} between {@code Pedido} and {@code ProductoPedido} in the legacy
 * (defect #1).
 */
@Entity
@Table(name = "cart_item")
@Getter
@Setter
public class CartItem {

    @EmbeddedId
    private CartItemId id;

    /**
     * The total quantity of the line, never an increment — {@code PUT} sets it (RF-03). {@code CHECK
     * (quantity > 0)} in the database is the last line of defence; the request is validated long before
     * it gets there.
     */
    @Column(nullable = false)
    private int quantity;

    /** For JPA only. */
    protected CartItem() {}

    public CartItem(CartItemId id, int quantity) {
        this.id = id;
        this.quantity = quantity;
    }

    /**
     * By identity, like every other entity here, except that the identity is the composite key: there is
     * no generated column that starts out {@code null}, so the "not yet persisted" guard the other
     * entities need has nothing to guard against.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CartItem item)) {
            return false;
        }
        return id != null && id.equals(item.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "CartItem{id=%s, quantity=%d}".formatted(id, quantity);
    }
}
