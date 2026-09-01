package dev.sandoretti.ukun.api.orders.application;

import dev.sandoretti.ukun.api.orders.infra.CheckoutAttemptRepository;
import dev.sandoretti.ukun.api.orders.infra.IdempotencyRecordRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The daily purge of the two checkout tables (ADR-0024, issue #118).
 *
 * <p>{@code docs/10-modelo-de-datos.md} §4 decided in writing that {@code idempotency_key} needs no
 * index on {@code created_at} <em>because</em> the purge is a daily sequential scan of a page or two,
 * and the v0.6 changeset made the same argument for {@code checkout_attempt}. Both arguments assumed
 * a purge that did not exist: the two tables have only grown since v0.6.
 *
 * <h2>Two windows, because they are two lifetimes</h2>
 *
 * An attempt is a thirty-minute draft (ADR-0016) and dies by its own {@code expires_at}, which the
 * service wrote when it opened the checkout. A row of {@code idempotency_key} is the record of a
 * confirmation, and it has to outlive the retry it exists to answer — {@code docs/11} §retention puts
 * that at a day. Sharing one window would mean either keeping drafts for a day or throwing away the
 * answer to a retry half an hour after it was given.
 *
 * <h2>Shape copied from {@code AccountReconciliationJob} in {@code ukun-auth}</h2>
 *
 * Same schedule as a property, same {@code @Transactional}, same INFO line only when it deleted
 * something — a daily "deleted 0 rows" is how an operator learns to skim past a logger
 * ({@code docs/11} §5). It emits no business event: that registry is closed ({@code docs/11} §3) and
 * a purge is technical state, not something that happened to a customer.
 */
@Component
public class CheckoutPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(CheckoutPurgeJob.class);

    private final CheckoutAttemptRepository attempts;
    private final IdempotencyRecordRepository idempotency;
    private final Clock clock;
    private final Duration idempotencyRetention;

    CheckoutPurgeJob(
            CheckoutAttemptRepository attempts,
            IdempotencyRecordRepository idempotency,
            Clock clock,
            @Value("${ukun.checkout.purge.idempotency-retention}") Duration idempotencyRetention) {

        this.attempts = attempts;
        this.idempotency = idempotency;
        this.clock = clock;
        this.idempotencyRetention = idempotencyRetention;
    }

    /**
     * Daily, at an hour nobody is deploying at, and behind a lock.
     *
     * <p>The lock is not what makes this correct — two replicas running {@code DELETE … WHERE} at once
     * is waste and not a defect, which is exactly the distinction ADR-0024 draws. It is here because
     * this is the job the next one will be copied from, and the next one may well be the one that
     * sends something.
     *
     * <p>No {@code lockAtLeastFor}: it absorbs clock skew between nodes, and the lock provider is
     * configured {@code usingDbTime}, so there is one clock and nothing to absorb. It would also make
     * the job silently skip for a minute after each run, which is how a test that calls it twice ends
     * up asserting nothing.
     */
    @Scheduled(cron = "${ukun.checkout.purge.cron}")
    @SchedulerLock(name = "checkout-purge", lockAtMostFor = "PT10M")
    @Transactional
    public void purgeExpiredCheckoutState() {
        Instant now = Instant.now(clock);

        long purgedAttempts = attempts.deleteExpiredBefore(now);
        long purgedKeys = idempotency.deleteCreatedBefore(now.minus(idempotencyRetention));

        if (purgedAttempts > 0 || purgedKeys > 0) {
            log.info("Purged {} expired checkout attempt(s) and {} idempotency record(s)", purgedAttempts, purgedKeys);
        }
    }
}
