package dev.sandoretti.ukun.auth;

import static dev.sandoretti.ukun.auth.AccountMother.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.auth.domain.Role;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.TestPropertySource;

/**
 * How many times one login is worth in the audit trail: once.
 *
 * <p>The registry of {@code docs/11-observabilidad-y-logging.md} §3 lists {@code auth.login.succeeded}
 * as the event of a person signing in, and a reader of those lines counts them — so emitting two per
 * login does not merely add noise, it reports a number that never happened.
 *
 * <p>This has to be an {@code *IT} and cannot be a unit test of the listener. The defect was never
 * in what the listener does with an event; it was in <em>which</em> event it subscribed to, and the
 * second publisher is the Authorization Server authenticating the {@code /oauth2/authorize} that
 * follows the login. Nothing short of the real flow has a second publisher in it, so a test that
 * hands the listener one event of each kind would have passed both before and after the fix.
 *
 * <h2>Why it pays for a context of its own</h2>
 *
 * The {@code @TestPropertySource} carries no setting anybody reads: its only job is to change the
 * context cache key, so this class gets its own {@link
 * dev.sandoretti.ukun.auth.security.LoginAttemptBuckets}. That bean is a singleton with state, and
 * its budget is five attempts per minute keyed on IP + email — while every login suite here runs
 * from the same loopback address inside the same minute. Sharing it, this class was the attempt that
 * pushed {@code LoginPageIT} past the limit, and the suite that failed was not the one that had
 * spent the budget.
 *
 * <p>An extra context costs a few seconds; the alternative is a suite whose green depends on how many
 * logins its neighbours happen to perform, which is the coupling {@code docs/13-testing.md} rules out
 * for shared mutable fixtures.
 */
@TestPropertySource(properties = "ukun.test.suite=login-events")
class LoginEventsIT extends LoginIntegrationTest {

    private static final String BUSINESS_LOGGER = "ukun.business";

    private static final String EVENT = "auth.login.succeeded";

    /**
     * Its own account, and not {@code AccountMother.customer()}, because the rate limiter of
     * {@code LoginAttemptBuckets} keys its five-per-minute bucket on IP + email and every suite in
     * this service logs in from the same loopback address. Sharing the mother's customer here means
     * this class spends a token of a bucket {@code LoginIT}, {@code TokenClaimsIT} and
     * {@code RpInitiatedLogoutIT} are already drawing from, and the suite that happens to run last
     * fails with an authorization endpoint that redirects to {@code /login} for a reason that has
     * nothing to do with what it was testing.
     */
    private static final String EMAIL = "login-events@ukun.dev";

    private final ListAppender<ILoggingEvent> lines = new ListAppender<>();

    private Logger businessLog;

    @BeforeEach
    void captureTheBusinessLog() {
        businessLog = (Logger) LoggerFactory.getLogger(BUSINESS_LOGGER);
        lines.start();
        businessLog.addAppender(lines);
    }

    @AfterEach
    void releaseTheBusinessLog() {
        businessLog.detachAppender(lines);
        lines.stop();
    }

    @Test
    @DisplayName("a full authorization code flow emits exactly one auth.login.succeeded")
    void emitsOneEventPerLoginAndNotOnePerAuthentication() {
        accounts().account(EMAIL, PASSWORD, true, Role.CUSTOMER);

        LoginAttempt attempt = logIn(EMAIL, PASSWORD);
        new AuthorizationCodeFlow(http, GATEWAY_SECRET).tokensFor(attempt.sessionCookie());

        assertThat(loginEvents())
                .as("one person signed in once; /oauth2/authorize authenticating again is not a second login")
                .hasSize(1);
    }

    /**
     * The other half of the same rule, and the one that would go unnoticed: a token exchange
     * authenticates the <em>client</em>, not a person. It never was a login, and no amount of
     * de-duplicating the previous case would say so.
     */
    @Test
    @DisplayName("client credentials never emit a login event")
    void emitsNothingWhenAServiceAuthenticates() {
        AccessTokens.responseOf(http, API_SECRET);

        assertThat(loginEvents()).isEmpty();
    }

    private List<String> loginEvents() {
        return lines.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith(EVENT))
                .toList();
    }
}
