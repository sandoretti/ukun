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
 * A line of a checkout attempt: the product, the price it was frozen at, and how many of it (ADR-0016).
 *
 * <p>It looks like {@link OrderLine} and is not the same thing. This one is a <strong>quote</strong>,
 * valid for thirty minutes: it says what the customer would pay if they confirmed now. The order line
 * is the record of what they did pay. The quote is what makes the price stable between the screen that
 * shows it and the request that accepts it — without it, a price change between the two would charge
 * an amount nobody was ever shown.
 *
 * <p>{@code sku} is carried alongside the name because the {@code INSUFFICIENT_STOCK} extension of
 * {@code docs/12-error-contract.md} has to name it when confirmation fails. Having it here is what
 * lets that path answer without going back to {@code catalog} for a product it has already read once.
 *
 * <p>No {@code totalPrice} column, unlike the order line: the attempt's total is stored once on the
 * attempt, and a per-line total would be a second place to keep the same arithmetic. The response
 * computes it ({@code CheckoutAttemptLine} of the contract carries no line total either).
 */
@Entity
@Table(name = "checkout_attempt_line")
@Getter
public class CheckoutAttemptLine {

    @EmbeddedId
    private CheckoutAttemptLineId id;

    /** {@code LAZY}, and nothing reads it: it is here so {@code @MapsId} can key the line by its attempt. */
    @MapsId("attemptId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, updatable = false)
    private CheckoutAttempt attempt;

    @Column(nullable = false, updatable = false, length = 32)
    private String sku;

    @Column(name = "product_name", nullable = false, updatable = false, length = 255)
    private String productName;

    /** The frozen price. {@code BigDecimal} over {@code DECIMAL(10,2)} — defect #5. */
    @Column(name = "unit_price", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, updatable = false)
    private int quantity;

    /** For JPA only. */
    protected CheckoutAttemptLine() {}

    public CheckoutAttemptLine(
            CheckoutAttempt attempt, Long productId, String sku, String productName, BigDecimal unitPrice, int quantity) {

        this.id = new CheckoutAttemptLineId(attempt.getId(), productId);
        this.attempt = attempt;
        this.sku = sku;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public Long getProductId() {
        return id.getProductId();
    }

    /** What this line costs, worked out rather than stored — see the class javadoc. */
    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CheckoutAttemptLine line)) {
            return false;
        }
        return id != null && id.equals(line.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(CheckoutAttemptLine.class);
    }

    /** Never walks {@link #attempt} — the same rule that keeps {@link OrderLine} free of defect #1. */
    @Override
    public String toString() {
        return "CheckoutAttemptLine[id=%s, quantity=%d]".formatted(id, quantity);
    }
}
