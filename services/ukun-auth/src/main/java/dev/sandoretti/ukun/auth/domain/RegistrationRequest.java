package dev.sandoretti.ukun.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * The association between an {@code Idempotency-Key} and the account that key reserved — the table
 * rule 6 of the {@code spring-auth} skill refers to, and what makes "idempotent" mean something
 * other than "we trust ukun-api not to retry" (docs/08 §3).
 *
 * <p><strong>The key is the primary key</strong>, not a column with an index next to a surrogate id.
 * That is what makes a concurrent replay a constraint violation the database decides, rather than a
 * check-then-insert with a race between the two halves — the same shape of defect as the legacy
 * {@code ClienteValidator}, and the reason {@code account.email} is unique for its own case.
 *
 * <p>Nothing ever references a row of this table, and the foreign key to {@code account} is
 * {@code ON DELETE CASCADE}: the reconciliation job deletes an abandoned reservation and the key goes
 * with it. Leaving the key behind would be worse than deleting it — retrying that same signup would
 * resolve to an account that no longer exists (baseline, changeset {@code 001-10}).
 */
@Entity
@Table(name = "registration_request")
@Getter
@Setter
public class RegistrationRequest {

    /**
     * The {@code registrationId} ukun-api issues for a signup and repeats verbatim on every retry of
     * it. Assigned, never generated: the value is chosen by the caller, which is the whole mechanism.
     */
    @Id
    @Column(name = "idempotency_key")
    private UUID idempotencyKey;

    /**
     * A plain UUID column and not a {@code @ManyToOne} to {@link Account}. The only thing anybody
     * asks this table is "which account did this key reserve?", and the answer is wanted <em>without</em>
     * loading the account — an association would turn the idempotent replay into a second select for
     * a row the caller only needs the id of.
     */
    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** Written by the column default; never by Hibernate, hence {@code insertable = false}. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** By id, and only by id — same reasoning as {@link Account#equals(Object)}. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RegistrationRequest request)) {
            return false;
        }
        return idempotencyKey != null && idempotencyKey.equals(request.idempotencyKey);
    }

    /** Constant, for the reason spelled out in {@link Account#hashCode()}. */
    @Override
    public int hashCode() {
        return Objects.hash(RegistrationRequest.class);
    }

    @Override
    public String toString() {
        return "RegistrationRequest[idempotencyKey=%s, accountId=%s]".formatted(idempotencyKey, accountId);
    }
}
