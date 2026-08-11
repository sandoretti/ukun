package dev.sandoretti.ukun.api.orders.infra;

import dev.sandoretti.ukun.api.orders.domain.IdempotencyRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
