package dev.sandoretti.ukun.api.orders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One confirmation of one checkout attempt, and what came of it (ADR-0016). The row that makes
 * {@code POST /api/orders} safe to send twice.
 *
 * <p><strong>What it protects against, precisely.</strong> A double click, a network retry after a
 * timeout, an F5 on the confirmation screen, the browser's back button. Disabling the button in React
 * covers the first of those four and nothing else, which is why the ADR insists it is defence in depth
 * rather than the mechanism.
 *
 * <p>{@code requestHash} is computed server-side over the attempt and the cart, so a key replayed
 * against a different cart is {@code 422 IDEMPOTENCY_MISMATCH} rather than an ambiguous answer. It is
 * a hex SHA-256, hence {@code VARCHAR(64)}.
 *
 * <p>{@code responseBody} is the stored answer, served verbatim on every replay. It is {@code JSONB}
 * in the schema and a {@code String} here with {@code @JdbcTypeCode(JSON)}: the service has no reason
 * to parse it back into an object — the point is to hand back exactly what the first request was
 * given, byte for byte.
 *
 * <p>The Java field is {@code idempotencyKey} while the column is {@code key}: {@code key} is a
 * function name in HQL, and a property called that is a query waiting to be misparsed. The column
 * keeps the name ADR-0016 gave it.
 */
@Entity
@Table(name = "idempotency_key")
@Getter
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The {@code attemptId}, echoed by the client as the {@code Idempotency-Key} header. */
    @Column(name = "key", nullable = false, updatable = false)
    private UUID idempotencyKey;

    /**
     * Unique together with the key ({@code ux_idempotency_customer_key}), and that pair is what makes
     * the whole mechanism safe under concurrency: two simultaneous confirmations of the same attempt
     * race to insert this row, and the database decides which one wins.
     */
    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdempotencyStatus status;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status")
    private Short responseStatus;

    @Column(name = "response_body")
    @JdbcTypeCode(SqlTypes.JSON)
    private String responseBody;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected IdempotencyRecord() {}

    /** A confirmation that has just started. Nothing about the outcome is known yet, hence the nulls. */
    public IdempotencyRecord(UUID idempotencyKey, Long customerId, String requestHash) {
        this.idempotencyKey = idempotencyKey;
        this.customerId = customerId;
        this.requestHash = requestHash;
        this.status = IdempotencyStatus.IN_PROGRESS;
    }

    /**
     * The outcome, stored so every later retry is answered from here instead of doing the work again.
     * The only transition this class has, and it is one-way.
     */
    public void complete(Long orderId, short responseStatus, String responseBody) {
        this.orderId = orderId;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.status = IdempotencyStatus.COMPLETED;
    }

    public boolean isCompleted() {
        return status == IdempotencyStatus.COMPLETED;
    }

    public boolean matches(String hash) {
        return requestHash.equals(hash);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IdempotencyRecord record)) {
            return false;
        }
        return id != null && id.equals(record.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(IdempotencyRecord.class);
    }

    /** No {@code responseBody}: it is a whole order, addresses included ({@code docs/11} §4). */
    @Override
    public String toString() {
        return "IdempotencyRecord[id=%s, key=%s, status=%s]".formatted(id, idempotencyKey, status);
    }
}
