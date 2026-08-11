package dev.sandoretti.ukun.api.orders.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/**
 * A checkout the customer has opened but not yet confirmed (ADR-0016): what they would buy, at what
 * price, and until when that answer stands.
 *
 * <p><strong>The server issues the key, and that is the decision this class exists to carry.</strong>
 * The browser used to generate the {@code Idempotency-Key}, which meant React had to guarantee it
 * survived a re-render without the server being able to check. Here {@link #getId()} is the
 * {@code attemptId} the contract returns and the {@code Idempotency-Key} that comes back at
 * confirmation, so "the key was regenerated on a re-render" stops being a class of bug that can exist.
 *
 * <p><strong>It reserves nothing.</strong> ADR-0016 rules a stock reservation with a TTL out by name —
 * an invariant, a table, an expiry job and a reconciliation path, for a benefit the attempt already
 * gives. Availability travels with the attempt as information, so {@code 409 INSUFFICIENT_STOCK} at
 * confirmation becomes the rare case of a real collision instead of the first the customer hears of it.
 *
 * <p>{@code createdAt} is assigned here rather than left to the column's {@code DEFAULT now()}, unlike
 * {@code Customer}: {@code ck_checkout_attempt_expires} compares it against {@code expires_at}, and a
 * pair where one value comes from the database clock and the other from the application's is a
 * constraint that depends on two clocks agreeing.
 */
@Entity
@Table(name = "checkout_attempt")
@Getter
public class CheckoutAttempt {

    /**
     * Thirty minutes, the figure ADR-0016 fixes. Long enough to walk away from a checkout and come
     * back, short enough that a frozen price is a promise the shop can keep.
     */
    public static final Duration LIFETIME = Duration.ofMinutes(30);

    /**
     * Assigned, not generated: the value has to exist before the row does, because the response that
     * carries it and the rows that reference it are built in the same transaction.
     */
    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    /** The frozen total. {@code BigDecimal} over {@code DECIMAL(10,2)} — defect #5. */
    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** Ordered by product, so the same attempt renders the same way twice — see {@code CustomerOrder.lines}. */
    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id.productId ASC")
    private List<CheckoutAttemptLine> lines = new ArrayList<>();

    /** For JPA only. */
    protected CheckoutAttempt() {}

    public CheckoutAttempt(Long customerId, Instant openedAt) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.createdAt = openedAt;
        this.expiresAt = openedAt.plus(LIFETIME);
    }

    public void addLine(Long productId, String sku, String productName, BigDecimal unitPrice, int quantity) {
        lines.add(new CheckoutAttemptLine(this, productId, sku, productName, unitPrice, quantity));
    }

    /**
     * Adds the lines up and freezes the total, exactly as {@code CustomerOrder.confirm()} does — the
     * two numbers have to be computed the same way, because the whole promise of the attempt is that
     * the amount confirmed is the amount that was shown.
     *
     * @throws IllegalStateException if there are no lines; the empty cart is a {@code 422 EMPTY_CART}
     *     decided before this object is built.
     */
    public CheckoutAttempt freeze() {
        if (lines.isEmpty()) {
            throw new IllegalStateException("A checkout attempt cannot be opened with no lines");
        }

        this.total = lines.stream()
                .map(CheckoutAttemptLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return this;
    }

    /** Whether the quote still stands at {@code now}. Expiry is a {@code 410}, never a silent repricing. */
    public boolean hasExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public List<CheckoutAttemptLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CheckoutAttempt attempt)) {
            return false;
        }
        return id != null && id.equals(attempt.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(CheckoutAttempt.class);
    }

    @Override
    public String toString() {
        return "CheckoutAttempt[id=%s, customerId=%s, lines=%d]".formatted(id, customerId, lines.size());
    }
}
