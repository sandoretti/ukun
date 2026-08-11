package dev.sandoretti.ukun.api.orders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/**
 * The identity of a line of a checkout attempt: the attempt and the product. Composite, like every
 * line table in this schema, so that one product cannot appear twice on the same attempt.
 *
 * <p>Unlike {@link OrderLineId}, both halves are known when the line is built: the attempt's key is a
 * UUID the server issues rather than a generated column, so there is nothing to wait for a flush to
 * find out.
 */
@Embeddable
@Getter
public class CheckoutAttemptLineId implements Serializable {

    @Column(name = "attempt_id", nullable = false)
    private UUID attemptId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** For JPA only. */
    protected CheckoutAttemptLineId() {}

    public CheckoutAttemptLineId(UUID attemptId, Long productId) {
        this.attemptId = attemptId;
        this.productId = productId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CheckoutAttemptLineId id)) {
            return false;
        }
        return Objects.equals(attemptId, id.attemptId) && Objects.equals(productId, id.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attemptId, productId);
    }

    @Override
    public String toString() {
        return "CheckoutAttemptLineId{attemptId=%s, productId=%s}".formatted(attemptId, productId);
    }
}
