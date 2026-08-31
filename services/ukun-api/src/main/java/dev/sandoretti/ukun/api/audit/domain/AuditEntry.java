package dev.sandoretti.ukun.api.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * One row of the trail: who did what to which resource, and when (ADR-0014). Owned by the
 * {@code audit} module.
 *
 * <p><strong>Append-only.</strong> No {@code @Setter} and no mutator of any kind — a trail that can be
 * edited answers no question worth asking. The constructor is the only way a row comes into being, and
 * there is no operation that changes one afterwards. {@code docs/11-observabilidad-y-logging.md} §2
 * extends the same reasoning to deletion: the table has no automatic purge by design, and if it ever
 * grows the answer is partitioning by date, not pruning.
 *
 * <p>{@code occurredAt} is filled by the database default ({@code now()}) and read back, hence
 * {@code insertable = false, updatable = false} — the same treatment {@code IdempotencyRecord} gives
 * its {@code createdAt}. The timestamp of an audit row should come from one clock, not from whichever
 * JVM happened to serve the request.
 *
 * <p>{@code details} is {@code JSONB} in the schema and a {@code String} here with
 * {@code @JdbcTypeCode(JSON)}, exactly like {@code IdempotencyRecord.responseBody}: nothing in this
 * service reads it back into an object. It is written once and read by a person, or by a query, later.
 *
 * <p>{@code resourceId} is text so that one column carries both the numeric ids of the catalogue and
 * the UUIDs of an account ({@code docs/11} §2). {@code traceId} is nullable and stays null when there is
 * no trace, which is the bridge to the full log of that request when there is one.
 */
@Entity
@Table(name = "audit_log")
@Getter
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", insertable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_account_id", nullable = false, updatable = false)
    private UUID actorAccountId;

    @Column(name = "actor_role", nullable = false, updatable = false, length = 20)
    private String actorRole;

    @Column(nullable = false, updatable = false, length = 50)
    private String action;

    @Column(name = "resource_type", nullable = false, updatable = false, length = 30)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false, length = 64)
    private String resourceId;

    @Column(updatable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String details;

    @Column(name = "trace_id", updatable = false, length = 32)
    private String traceId;

    /** For JPA only. */
    protected AuditEntry() {}

    public AuditEntry(
            UUID actorAccountId,
            String actorRole,
            String action,
            String resourceType,
            String resourceId,
            String details,
            String traceId) {

        this.actorAccountId = actorAccountId;
        this.actorRole = actorRole;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.details = details;
        this.traceId = traceId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuditEntry entry)) {
            return false;
        }
        return id != null && id.equals(entry.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(AuditEntry.class);
    }

    /**
     * No {@code details}: it is an open field, and although {@code docs/11} §4 forbids putting anything
     * personal in it, a {@code toString()} that prints it would make every future mistake there a
     * logged one. The four fields here are what identifies the row.
     */
    @Override
    public String toString() {
        return "AuditEntry[id=%s, action=%s, resourceType=%s, resourceId=%s]"
                .formatted(id, action, resourceType, resourceId);
    }
}
