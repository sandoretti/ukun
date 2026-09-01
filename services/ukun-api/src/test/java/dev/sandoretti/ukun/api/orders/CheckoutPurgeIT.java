package dev.sandoretti.ukun.api.orders;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.api.orders.application.CheckoutAttemptResponse;
import dev.sandoretti.ukun.api.orders.application.CheckoutPurgeJob;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * That the two checkout tables stop growing (ADR-0024, issue #118 point 3).
 *
 * <p>{@code docs/10} §4 decided in writing that {@code idempotency_key} needs no index on
 * {@code created_at} <em>because</em> a daily purge is a scan of a page or two, and the v0.6 changeset
 * said the same about {@code checkout_attempt}. Both arguments assumed a job that did not exist until
 * v1.0, so the tables had only grown since the checkout shipped.
 *
 * <p>It extends {@link OrdersIntegrationTest} rather than opening a context of its own, which also
 * gets it the two things this needs: rows written through the real endpoints, and the movable clock —
 * a purge whose subject is a deadline cannot be tested against a clock that does not move without
 * rewriting the very columns the job reads (skill {@code testing}).
 *
 * <p>The negative half of each test is the half that matters. A purge that deleted everything would
 * pass a test that only checked that old rows disappear, and it would silently throw away the answer
 * to a retry that is still in its window.
 */
class CheckoutPurgeIT extends OrdersIntegrationTest {

    @Autowired
    private CheckoutPurgeJob purge;

    @Test
    @DisplayName("deletes an attempt past its expiry and keeps one still inside it")
    void purgesOnlyExpiredAttempts() {
        addToCart(ADA, productIdOf(IN_STOCK), 1);
        CheckoutAttemptResponse stale = openAttempt(ADA);
        clock.advance(Duration.ofHours(1));
        addToCart(GRACE, productIdOf(IN_STOCK), 1);
        CheckoutAttemptResponse fresh = openAttempt(GRACE);

        purge.purgeExpiredCheckoutState();

        assertThat(attemptRows(stale.attemptId())).isZero();
        assertThat(attemptRows(fresh.attemptId())).isOne();
    }

    /**
     * The lines go with the attempt through the {@code ON DELETE CASCADE} of the v0.6 changeset and
     * not through JPA: the job issues a bulk delete, which does not cascade in the persistence
     * context. If the constraint were ever dropped, this is what would notice.
     */
    @Test
    @DisplayName("an expired attempt takes its frozen lines with it")
    void purgesTheLinesOfAnExpiredAttempt() {
        addToCart(ADA, productIdOf(IN_STOCK), 1);
        CheckoutAttemptResponse stale = openAttempt(ADA);
        clock.advance(Duration.ofHours(1));

        purge.purgeExpiredCheckoutState();

        assertThat(lineRows(stale.attemptId())).isZero();
    }

    /**
     * A day, not thirty minutes: the record of a confirmation has to outlive the retry it exists to
     * answer ({@code docs/11}, retention). Sharing the attempt's window would throw that answer away
     * half an hour after it was given.
     */
    @Test
    @DisplayName("keeps an idempotency record until its own, longer window has passed")
    void purgesIdempotencyRecordsOnTheirOwnWindow() {
        placeOrder(ADA, IN_STOCK, 1);

        clock.advance(Duration.ofHours(1));
        purge.purgeExpiredCheckoutState();
        assertThat(idempotencyRows()).isOne();

        clock.advance(Duration.ofDays(2));
        purge.purgeExpiredCheckoutState();
        assertThat(idempotencyRows()).isZero();
    }

    private int attemptRows(UUID attemptId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM checkout_attempt WHERE id = ?", Integer.class, attemptId);
    }

    private int lineRows(UUID attemptId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM checkout_attempt_line WHERE attempt_id = ?", Integer.class, attemptId);
    }

    private int idempotencyRows() {
        return jdbc.queryForObject("SELECT count(*) FROM idempotency_key", Integer.class);
    }
}
