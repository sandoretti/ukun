package dev.sandoretti.ukun.api.orders.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.Getter;

/**
 * An order: what somebody bought, at what price, sent where and paid with what (RF-04). The aggregate
 * root of the {@code orders} module, and the only entity in this service that is written once and
 * never edited.
 *
 * <p><strong>Everything about this class is about not being editable.</strong> No setters, no public
 * mutators, columns declared {@code updatable = false}, lines returned unmodifiable, and the two
 * snapshots embedded rather than referenced. An order is a record of something that happened; the
 * legacy treats it as a view over the customer's current profile, which is why moving house rewrites
 * the shipping address of orders placed years earlier (defect #19, ADR-0011).
 *
 * <p><strong>{@code customerId} is a bare {@code Long}</strong>, one of the six associations ADR-0017
 * §2 turns into an identifier: the foreign key stays in the database and the navigation into
 * {@code customers} disappears from Java. The address and the card are not read through it either —
 * they are copied in at construction time, which is the whole point.
 *
 * <p><strong>{@code total} is stored, unlike the cart's.</strong> The cart computes its total on every
 * read because prices move under it by design; an order's total is the amount the customer agreed to
 * pay, and recomputing it later from a catalogue that has since changed would produce a different
 * number for the same order. {@code ck_customer_order_total} keeps it above zero, which makes the
 * legacy's "empty cart becomes an order with a total of 0" unrepresentable.
 *
 * <p>{@code @Getter} and never {@code @Data}: the hard rule of {@code CLAUDE.md}, and this is the
 * class where it was earned — {@code @Data} on the legacy {@code Pedido}, with its mutual reference to
 * {@code ProductoPedido}, is defect #1, a real {@code StackOverflowError}.
 */
@Entity
@Table(name = "customer_order")
@Getter
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    /**
     * {@code VARCHAR(20)} with a {@code CHECK}, not a Postgres {@code ENUM} — ADR-0012 explains why at
     * length, the short version being that adding a value to a native enum cannot be used in the same
     * transaction that adds it, and Liquibase wraps each changeset in one.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private OrderStatus status;

    /** {@code BigDecimal} over {@code DECIMAL(10,2)}, never {@code Float} — defect #5. */
    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Embedded
    private ShippingAddress shippingAddress;

    @Embedded
    private PaymentSnapshot payment;

    /**
     * {@code CascadeType.ALL} with {@code orphanRemoval}: the lines have no existence outside the
     * order, so the aggregate is written in one {@code save} and the lines are never saved by a
     * repository of their own — there is not one.
     *
     * <p>{@code LAZY} by default and fetched with an {@code @EntityGraph} where the response needs it
     * ({@code CustomerOrderRepository}). An {@code EAGER} collection would be the N+1 of defect #12 on
     * the order history, the exact listing the legacy gets wrong.
     *
     * <p>{@code @OrderBy} for the same reason the cart orders its lines and every {@code Sort} of this
     * API ends in {@code id} (defect #23): without it the lines come back in whatever order the join
     * produced, so reading the same order twice could render it differently. Product id is total
     * within an order — the primary key says so.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id.productId ASC")
    private List<OrderLine> lines = new ArrayList<>();

    /** For JPA only. */
    protected CustomerOrder() {}

    /**
     * A confirmed order with its snapshots. The lines are added right after with {@link #addLine} and
     * the total is worked out from them by {@link #confirm()}, which is also where the aggregate checks
     * that it has any.
     */
    public CustomerOrder(Long customerId, Instant placedAt, ShippingAddress shippingAddress, PaymentSnapshot payment) {
        this.customerId = customerId;
        this.placedAt = placedAt;
        this.status = OrderStatus.CONFIRMED;
        this.shippingAddress = shippingAddress;
        this.payment = payment;
    }

    public void addLine(Long productId, String productName, BigDecimal unitPrice, int quantity) {
        lines.add(new OrderLine(this, productId, productName, unitPrice, quantity));
    }

    /**
     * Adds the lines up and freezes the total. The one place the amount is computed, so no caller can
     * hand in a number the lines do not support.
     *
     * @throws IllegalStateException if there are no lines. It is a broken invariant rather than a
     *     client error: the empty cart is rejected as {@code 422 EMPTY_CART} well before an aggregate
     *     is built, and reaching this line means the checkout skipped that check.
     */
    public CustomerOrder confirm() {
        if (lines.isEmpty()) {
            throw new IllegalStateException("An order cannot be confirmed without lines");
        }

        this.total = lines.stream()
                .map(OrderLine::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return this;
    }

    /** Unmodifiable: adding a line to a finished order is not an operation this domain has. */
    public List<OrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CustomerOrder order)) {
            return false;
        }
        return id != null && id.equals(order.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(CustomerOrder.class);
    }

    /**
     * Identifiers and a count. No address, no card, no line-by-line breakdown: this object travels
     * through the whole checkout path, and {@code docs/11-observabilidad-y-logging.md} §4 forbids an
     * entity reaching a log line with personal data on it — which is exactly how the legacy prints
     * PANs (defects #20a and #20b).
     */
    @Override
    public String toString() {
        return "CustomerOrder[id=%s, customerId=%s, lines=%d]".formatted(id, customerId, lines.size());
    }
}
