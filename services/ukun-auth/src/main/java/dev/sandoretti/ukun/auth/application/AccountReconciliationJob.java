package dev.sandoretti.ukun.auth.application;

import dev.sandoretti.ukun.auth.infra.AccountRepository;
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
 * Deletes accounts that were reserved and never activated — rule 7 of the {@code spring-auth} skill.
 *
 * <p><strong>This, and not {@code :release}, is what makes the saga correct.</strong> The
 * compensation is best-effort by design: ukun-api calls it when its local transaction fails, but that
 * call can itself fail, and the process running the saga can die between the two steps. Neither case
 * needs handling, because a reservation that nobody completes is inert — a disabled account allows no
 * login — and this job collects it. {@code docs/08-identidad-y-seguridad.md} §3 states the property
 * plainly: the system is correct even if the compensation of step 3 fails.
 *
 * <p>The cascade does the rest. Deleting the account takes its roles and its idempotency key with it,
 * and taking the key matters: leaving it behind would let a retry of that signup resolve to an account
 * that no longer exists (baseline, changeset {@code 001-10}).
 *
 * <h2>Why the threshold is hours and not minutes</h2>
 *
 * A signup takes seconds, so anything older than that is over — but the window is not a timeout, it is
 * a safety margin against deleting a reservation whose saga is still running somewhere. Twenty-four
 * hours is what §3 fixes, and the cost of being generous is a handful of dead rows in a table of
 * hundreds.
 *
 * <p>No index backs the query, on purpose: {@code docs/10-modelo-de-datos.md} §4 justifies each index
 * by the query that motivates it, and a daily sequential scan over a small table motivates nothing
 * (baseline, changeset {@code 001-11}).
 */
@Component
public class AccountReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(AccountReconciliationJob.class);

    private final AccountRepository accounts;
    private final Duration retention;

    AccountReconciliationJob(
            AccountRepository accounts, @Value("${ukun.reconciliation.retention:PT24H}") Duration retention) {
        this.accounts = accounts;
        this.retention = retention;
    }

    /**
     * Daily, at an hour nobody is deploying at. {@code @EnableScheduling} is on
     * {@code UkunAuthApplication} rather than here because it is a global switch.
     *
     * <p>The schedule is a property so a test can run the service without waiting for it, and so an
     * operator can move it. The default is the behaviour {@code docs/08} §3 describes.
     *
     * <p><strong>Behind a lock since v1.0</strong> (ADR-0024), and not because two replicas running
     * this at once would be wrong — it is a {@code DELETE … WHERE} and the second one would delete
     * nothing. It is the template the next job in this service gets copied from, and by then the
     * distinction may have stopped being free.
     *
     * <p><strong>No {@code lockAtLeastFor}</strong>, and that omission is load-bearing. It exists to
     * absorb clock skew between nodes, and {@code SchedulerLockConfig} configures {@code usingDbTime}
     * so there is only one clock to skew. Keeping it would make the job silently unrunnable for a
     * minute after every run — which is not a production problem and is exactly how
     * {@link dev.sandoretti.ukun.auth.AccountReconciliationIT} would start passing without running
     * anything.
     */
    @Scheduled(cron = "${ukun.reconciliation.cron:0 30 3 * * *}")
    @SchedulerLock(name = "account-reconciliation", lockAtMostFor = "PT10M")
    @Transactional
    public void collectAbandonedReservations() {
        // No injected Clock. What decides the outcome is `created_at`, which the database writes and a
        // test can set to any instant it likes — so a fake clock would add a seam without buying a
        // single assertion that AccountReconciliationIT cannot make by writing an old row.
        Instant threshold = Instant.now().minus(retention);

        long collected = accounts.deleteByEnabledFalseAndCreatedAtBefore(threshold);

        // At INFO only when it did something. A daily "deleted 0 accounts" is the kind of line that
        // teaches an operator to skim past this logger (docs/11 §5).
        if (collected > 0) {
            log.info("Reconciliation deleted {} account(s) reserved before {}", collected, threshold);
        }
    }
}
