package dev.sandoretti.ukun.api.orders.infra;

import dev.sandoretti.ukun.api.orders.domain.IdempotencyRecord;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@code idempotency_key}: the record of every confirmation, so that a repeated one is answered rather
 * than repeated (ADR-0016).
 *
 * <p>One query, on {@code ux_idempotency_customer_key}, which {@code docs/10} §4 lists as "the only
 * query of this table". The customer is part of it for the reason it is part of every other look-up on
 * this path: a key names something within the caller's own account and never grants access across
 * accounts.
 */
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByCustomerIdAndIdempotencyKey(Long customerId, UUID idempotencyKey);

    /**
     * The daily purge {@code docs/10} §4 assumed existed when it decided this table needs no index on
     * {@code created_at} (ADR-0024). One statement, for the reason the sibling repository gives.
     *
     * <p>{@code IN_PROGRESS} rows are deleted too, and that is deliberate rather than an oversight of
     * the {@code WHERE}: past the window, one is not a confirmation in flight — it is a confirmation
     * that died holding the key, and leaving it would keep answering {@code 409
     * IDEMPOTENCY_IN_PROGRESS} to that customer for that key for ever.
     */
    @Modifying
    @Query("delete from IdempotencyRecord record where record.createdAt < :threshold")
    long deleteCreatedBefore(@Param("threshold") Instant threshold);
}
