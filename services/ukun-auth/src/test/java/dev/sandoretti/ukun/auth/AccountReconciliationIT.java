package dev.sandoretti.ukun.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.auth.application.AccountReconciliationJob;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The guarantee the signup saga actually rests on: a reservation nobody completed does not stay
 * forever.
 *
 * <p>{@code docs/08-identidad-y-seguridad.md} §3 is explicit that the system is correct <em>even if
 * the compensation of step 3 fails</em>, and this is why — {@code :release} is a fast path, this job
 * is the guarantee. Without it, every crashed signup would leave an email locked out of the system
 * permanently.
 *
 * <p>The job is invoked directly rather than by waiting for its cron. What is under test is which
 * rows it deletes, not whether Spring can read a cron expression, and a test that slept until 03:30
 * would assert the second thing at the cost of the first.
 *
 * <p>{@code created_at} is written by the database default, so a row "from yesterday" is made by
 * updating it afterwards. That is also why the job needs no injected clock: the test moves the row,
 * not the world.
 */
@SpringBootTest(
        properties = {
            "spring.liquibase.contexts=prod",
            "ukun.oauth2.gateway.secret=" + AbstractIntegrationTest.GATEWAY_SECRET,
            "ukun.oauth2.api.secret=" + AbstractIntegrationTest.API_SECRET,
            "ukun.password.compromised-check.enabled=false",
            // A cron that never fires within a test run: the only invocation is the explicit one below,
            // so the schedule cannot race with the assertions.
            "ukun.reconciliation.cron=0 0 0 29 2 ?"
        })
class AccountReconciliationIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, RECONCILIATION_DATABASE);
    }

    @Autowired
    private AccountReconciliationJob job;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("a reservation older than the retention window is deleted, and its key with it")
    void collectsAnAbandonedReservation() {
        AccountMother mother = new AccountMother(jdbc, passwordEncoder);
        UUID abandoned = mother.reserved("abandoned-" + UUID.randomUUID() + "@ukun.dev");
        UUID key = registerKeyFor(abandoned);
        agedBy(abandoned, Duration.ofHours(25));

        job.collectAbandonedReservations();

        assertThat(accountsWithId(abandoned)).isZero();
        // The cascade of changeset 001-10. A key left pointing at a deleted account would make a retry
        // of that signup resolve to something that no longer exists.
        assertThat(registrationsUnder(key)).isZero();
    }

    /**
     * The margin the twenty-four hours are there for. A signup takes seconds, but a saga that is
     * still running must not have its reservation pulled from under it.
     */
    @Test
    @DisplayName("a reservation made an hour ago is left alone")
    void leavesARecentReservationAlone() {
        AccountMother mother = new AccountMother(jdbc, passwordEncoder);
        UUID recent = mother.reserved("recent-" + UUID.randomUUID() + "@ukun.dev");
        agedBy(recent, Duration.ofHours(1));

        job.collectAbandonedReservations();

        assertThat(accountsWithId(recent)).isEqualTo(1);
    }

    /**
     * The rule is "disabled", not "old". An account that completed its saga is a real customer, and
     * age is no reason to touch it — this is the assertion that stands between a reconciliation job
     * and a data-loss incident.
     */
    @Test
    @DisplayName("an activated account is never collected, however old")
    void neverCollectsAnActivatedAccount() {
        AccountMother mother = new AccountMother(jdbc, passwordEncoder);
        UUID active = mother.account("veteran-" + UUID.randomUUID() + "@ukun.dev", AccountMother.PASSWORD, true);
        agedBy(active, Duration.ofDays(400));

        job.collectAbandonedReservations();

        assertThat(accountsWithId(active)).isEqualTo(1);
    }

    /** A registration_request pointing at {@code accountId}, as a real reservation would leave. */
    private UUID registerKeyFor(UUID accountId) {
        UUID key = UUID.randomUUID();
        jdbc.update("INSERT INTO registration_request (idempotency_key, account_id) VALUES (?, ?)", key, accountId);

        return key;
    }

    private void agedBy(UUID accountId, Duration age) {
        jdbc.update(
                "UPDATE account SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minus(age)),
                accountId);
    }

    private int accountsWithId(UUID accountId) {
        return jdbc.queryForObject("SELECT count(*) FROM account WHERE id = ?", Integer.class, accountId);
    }

    private int registrationsUnder(UUID key) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM registration_request WHERE idempotency_key = ?", Integer.class, key);
    }
}
