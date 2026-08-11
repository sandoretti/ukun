package dev.sandoretti.ukun.api.orders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.Getter;

/**
 * One product on an order: what it was called, what it cost and how many of it (RF-04).
 *
 * <p><strong>Three frozen values, and the third one is the one everybody forgets.</strong> RF-04
 * freezes the unit price, and the legacy does keep that one. It does not keep the <em>name</em>: an
 * admin renaming "Sofá de cuero" to "Sofá de piel" — which RF-10 allows — would rewrite what every
 * past order displays. {@code product_name} as a snapshot closes that, and as a side effect keeps a
 * finished order readable after the product is discontinued (ADR-0011, {@code docs/10} §5).
 *
 * <p>{@code totalPrice} is stored rather than computed on read, and the database checks it:
 * {@code ck_order_line_total} asserts {@code total_price = unit_price * quantity}. That is not
 * redundancy for its own sake — it is the aggregate's invariant bought for one line of DDL, and it
 * makes an order whose lines do not add up impossible to insert from any code path at all.
 *
 * <p><strong>{@code productId} is a bare {@code Long}, inside {@link OrderLineId}.</strong> The
 * association crosses into {@code catalog} and ADR-0017 §2 lists it among the six that become
 * identifiers: the foreign key stays in the database, the navigation disappears from Java. The name and
 * the price do not come from there anyway — they are this line's own copy.
 *
 * <p><strong>The reference back to the order is where defect #1 was born.</strong> The legacy has
 * {@code Pedido} and {@code ProductoPedido} pointing at each other with Lombok {@code @Data} on both,
 * so {@code toString()} recurses into a {@code StackOverflowError} — and so do {@code equals} and
 * {@code hashCode}. The reference is kept, because {@code @MapsId} needs it to fill in the generated
 * order identifier, and the three methods are written by hand so that none of them ever walks it.
 */
@Entity
@Table(name = "order_line")
@Getter
public class OrderLine {

    @EmbeddedId
    private OrderLineId id;

    /**
     * {@code @MapsId} is what makes the composite key work with a generated order identifier: Hibernate
     * copies {@code order.id} into {@code id.orderId} when the order is flushed, so nothing here has to
     * know the value before the insert.
     *
     * <p>{@code LAZY}, though nothing reads it: an {@code EAGER} {@code @ManyToOne} — the default of
     * the legacy's {@code @OneToOne}s — is one of the causes of the N+1 of defect #12, and a line
     * loading its order back is a query with no purpose at all.
     */
    @MapsId("orderId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private CustomerOrder order;

    @Column(name = "product_name", nullable = false, updatable = false, length = 255)
    private String productName;

    /** {@code DECIMAL(10,2)} mapped to {@code BigDecimal}, never {@code Float} — defect #5. */
    @Column(name = "unit_price", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "total_price", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    /** For JPA only. */
    protected OrderLine() {}

    /**
     * A line, with its total worked out here rather than by the caller. There is exactly one way to
     * compute it and the database is about to check the answer, so leaving it to be passed in would
     * only create the opportunity to pass a different one.
     */
    public OrderLine(CustomerOrder order, Long productId, String productName, BigDecimal unitPrice, int quantity) {
        this.id = new OrderLineId(productId);
        this.order = order;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Long getProductId() {
        return id.getProductId();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OrderLine line)) {
            return false;
        }
        return id != null && id.equals(line.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(OrderLine.class);
    }

    /** By identifier, and it never touches {@link #order} — see the class javadoc, defect #1. */
    @Override
    public String toString() {
        return "OrderLine[id=%s, quantity=%d]".formatted(id, quantity);
    }
}
