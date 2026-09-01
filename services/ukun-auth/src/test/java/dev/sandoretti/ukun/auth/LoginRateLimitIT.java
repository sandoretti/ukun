package dev.sandoretti.ukun.auth;

import static dev.sandoretti.ukun.auth.AccountMother.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.auth.domain.Role;
import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.postgresql.Bucket4jPostgreSQL;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * The login attempt limit of {@code docs/08-identidad-y-seguridad.md} §8, which is what stands
 * between this service and an offline-speed password guess against a known email.
 *
 * <p>Each test uses an email of its own. The buckets live in the application context and the context
 * is shared across this suite, so tests that shared a key would consume each other's budget and the
 * one that ran second would fail for a reason that has nothing to do with what it asserts.
 */
class LoginRateLimitIT extends LoginIntegrationTest {

    /** Matches the burst bandwidth of {@code LoginAttemptBuckets}. */
    private static final int BURST_CAPACITY = 5;

    /**
     * The budget is one budget and not one per replica (ADR-0024, issue #118 point 5) — the fact that
     * makes this service deployable with more than one instance.
     *
     * <p>Asserted with a second {@code ProxyManager} over the same database, which is what a second
     * replica <em>is</em> as far as this budget is concerned: it shares nothing with the application's
     * bean except the table. Before v1.0 the buckets were a {@code ConcurrentHashMap}, so this second
     * view would have started with a full five and the real budget would have been N×5 per minute.
     *
     * <p>It goes through the HTTP form rather than the bean so that what is being spent is the budget
     * the limiter actually charges, key included — a test that consumed a key of its own invention
     * would pass while the filter used another.
     */
    @Test
    @DisplayName("another replica sees the same budget already spent")
    void sharesTheBudgetWithEveryOtherReplica() {
        String email = "shared-budget@ukun.dev";
        accounts().account(email, PASSWORD, true, Role.CUSTOMER);

        IntStream.range(0, BURST_CAPACITY).forEach(attempt -> logIn(email, "wrong-password"));

        assertThat(remainingTokensOfAnotherReplica(email)).isZero();
    }

    /**
     * Reads the row the limiter wrote, through the library's own API and a datasource of its own. The
     * key is the one {@code LoginAttemptBuckets} composes: caller address and lower-cased email.
     */
    private long remainingTokensOfAnotherReplica(String email) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                jdbcUrlOf(ACCOUNTS_DATABASE), username(), password());

        ProxyManager<String> otherReplica = Bucket4jPostgreSQL.selectForUpdateBasedBuilder(dataSource)
                .primaryKeyMapper(PrimaryKeyMapper.STRING)
                .table("login_attempt_bucket")
                .idColumn("id")
                .stateColumn("state")
                .expiresAtColumn("expires_at")
                .build();

        return otherReplica
                .getProxy("127.0.0.1|" + email, () -> {
                    throw new IllegalStateException("The limiter should have created this bucket already");
                })
                .getAvailableTokens();
    }

    /**
     * The attempts are made with a valid CSRF token, which is the only way this test measures what it
     * claims to. The limiter sits after {@code CsrfFilter} — deliberately, because a request without
     * a token is rejected at near-zero cost and never reaches the BCrypt verification that the
     * limiter exists to ration — so an attempt with a bogus token is answered by CSRF and says
     * nothing about throttling.
     */
    @Test
    @DisplayName("a run of failed attempts against one account is cut off with 429 and Retry-After")
    void throttlesRepeatedFailures() {
        String email = "throttled@ukun.dev";
        accounts().account(email, PASSWORD, true, Role.CUSTOMER);

        IntStream.range(0, BURST_CAPACITY).forEach(attempt -> logIn(email, "wrong-password"));

        LoginAttempt blocked = logIn(email, "wrong-password");

        assertThat(blocked.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(blocked.retryAfter()).isNotNull();
    }

    /**
     * And the right password does not buy a way past it either: a bucket that is empty is empty. If
     * a correct credential skipped the limiter, an attacker who found the password on the last
     * allowed attempt would face no delay at all.
     */
    @Test
    @DisplayName("being throttled is not undone by finally getting the password right")
    void theCorrectPasswordDoesNotBypassTheLimit() {
        String email = "persistent@ukun.dev";
        accounts().account(email, PASSWORD, true, Role.CUSTOMER);

        IntStream.range(0, BURST_CAPACITY).forEach(attempt -> logIn(email, "wrong-password"));

        assertThat(logIn(email, PASSWORD).status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    /**
     * The property that makes this a rate limiter and not a lockout. Counting failures per account
     * alone would let anybody disable an account they know the address of, simply by failing to log
     * into it — turning the protection into the attack.
     */
    @Test
    @DisplayName("throttling one account leaves another one able to log in")
    void oneAccountCannotLockOutAnother() {
        String victim = "victim@ukun.dev";
        String noisy = "noisy@ukun.dev";
        accounts().account(victim, PASSWORD, true, Role.CUSTOMER);
        accounts().account(noisy, PASSWORD, true, Role.CUSTOMER);

        IntStream.range(0, BURST_CAPACITY + 2).forEach(attempt -> logIn(noisy, "wrong-password"));

        assertThat(logIn(victim, PASSWORD).succeeded()).isTrue();
    }

    /**
     * Fetching the login page is not an attempt. If it consumed budget, every reload of a page that a
     * browser may request more than once would eat into the allowance of somebody who has not yet
     * typed anything.
     */
    @Test
    @DisplayName("opening the login page consumes no budget")
    void readingTheFormIsNotAnAttempt() {
        String email = "patient@ukun.dev";
        accounts().account(email, PASSWORD, true, Role.CUSTOMER);

        IntStream.range(0, 20)
                .forEach(attempt -> http.get().uri(FormLogin.LOGIN_PATH).exchange().expectStatus().isOk());

        assertThat(logIn(email, PASSWORD).succeeded()).isTrue();
    }
}
