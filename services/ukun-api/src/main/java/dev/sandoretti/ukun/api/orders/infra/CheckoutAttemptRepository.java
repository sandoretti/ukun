package dev.sandoretti.ukun.api.orders.infra;

import dev.sandoretti.ukun.api.orders.domain.CheckoutAttempt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * {@code checkout_attempt} and, through the aggregate's cascade, its lines.
 *
 * <p>Two operations in the whole life of this table: insert one, read it back once at confirmation.
 * There is no listing, no search and no index beyond the primary key — {@code docs/10} §4 makes the
 * same argument for {@code idempotency_key}, and it applies here for the same reason.
 */
public interface CheckoutAttemptRepository extends JpaRepository<CheckoutAttempt, UUID> {

    /**
     * The attempt being confirmed, with its frozen lines and scoped to its owner.
     *
     * <p>{@code AndCustomerId} for the reason {@code CustomerOrderRepository.findByIdAndCustomerId}
     * gives: an {@code attemptId} arriving in a header identifies an attempt and authorises nothing.
     * Somebody else's attempt has to look exactly like one that never existed — which here means
     * {@code 410 CHECKOUT_EXPIRED}, the same answer a stale one gets, and deliberately so: two
     * different answers would let a caller probe for other people's attempts.
     *
     * <p>The {@code @EntityGraph} makes it one statement. The lines are the whole reason to read the
     * attempt at all — they are the order about to be written — so fetching them lazily would be an
     * extra round trip on the one path in this service that holds database locks.
     */
    @EntityGraph(attributePaths = "lines")
    Optional<CheckoutAttempt> findByIdAndCustomerId(UUID id, Long customerId);
}
