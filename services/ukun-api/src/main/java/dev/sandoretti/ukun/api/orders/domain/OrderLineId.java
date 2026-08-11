package dev.sandoretti.ukun.api.orders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;

/**
 * The identity of an order line: the order and the product, together — the primary key of
 * {@code order_line} in the baseline.
 *
 * <p>Composite for the same reason {@code CartItemId} is: one line per {@code (order, product)},
 * enforced by the database instead of by whoever remembers it. An order with the same product on two
 * lines would show the customer two prices for one thing and make its total impossible to check.
 *
 * <p>{@code orderId} is <strong>null until the order is flushed</strong>: the order's key is generated,
 * so the value only exists once the row is inserted. {@link OrderLine} declares {@code @MapsId} for
 * exactly that, and it is why this class has a setter-free constructor taking only the product — the
 * other half is Hibernate's to fill in.
 *
 * <p>A class and not a {@code record}, per {@code CartItemId}: JPA requires an identifier class to be
 * {@link Serializable} with a no-argument constructor, and Hibernate's support for records as an
 * {@code @EmbeddedId} is not something to discover in production.
 */
@Embeddable
@Getter
public class OrderLineId implements Serializable {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** For JPA only. */
    protected OrderLineId() {}

    /** The half that is known when the line is built. See the class javadoc for the other one. */
    public OrderLineId(Long productId) {
        this.productId = productId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OrderLineId id)) {
            return false;
        }
        return Objects.equals(orderId, id.orderId) && Objects.equals(productId, id.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, productId);
    }

    @Override
    public String toString() {
        return "OrderLineId{orderId=%s, productId=%s}".formatted(orderId, productId);
    }
}
