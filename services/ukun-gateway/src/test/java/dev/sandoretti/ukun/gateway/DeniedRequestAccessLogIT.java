package dev.sandoretti.ukun.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * That a refused request leaves a line, asserted against the real security chain rather than a stand-in
 * for it.
 *
 * <p>{@code AccessLogWebFilterTest} can only fake a refusal with a chain that sets a status, which is
 * a description of what {@code WebFilterChainProxy} does and not the thing itself. The defect this
 * class pins down was precisely a mismatch between the two: the filter did log every response it was
 * handed, and never got handed the denied ones, because the chain completes without calling the
 * filters below it. Only the assembled chain, in its real order, can tell the difference.
 *
 * <p>It matters because the gateway is the single point of entry ({@code docs/11} §6, last row). While
 * these lines were missing, an attempt against the admin surface — anonymous or by somebody who simply
 * lacked the role — was invisible in the logs of the one service every request goes through.
 */
class DeniedRequestAccessLogIT extends FilterChainIntegrationTest {

    private static final String ACCESS_LOGGER = "ukun.access";

    private static final String ADMIN_SURFACE = "/api/admin/products";

    private final ListAppender<ILoggingEvent> lines = new ListAppender<>();

    private Logger accessLog;

    @BeforeEach
    void captureTheAccessLog() {
        accessLog = (Logger) LoggerFactory.getLogger(ACCESS_LOGGER);
        lines.start();
        accessLog.addAppender(lines);
    }

    @AfterEach
    void releaseTheAccessLog() {
        accessLog.detachAppender(lines);
        lines.stop();
    }

    @Test
    @DisplayName("a 401 from the security chain is logged like any other response")
    void logsTheAnonymousDenial() {
        http.get().uri(ADMIN_SURFACE).exchange().expectStatus().isUnauthorized();

        awaitLineFor(ADMIN_SURFACE);

        assertThat(linesFor(ADMIN_SURFACE)).singleElement().satisfies(line -> assertSoftly(softly -> {
            softly.assertThat(line.getFormattedMessage()).contains("401");
            softly.assertThat(line.getLevel()).isEqualTo(Level.WARN);
            softly.assertThat(line.getMDCPropertyMap()).doesNotContainKey("accountId");
        }));
    }

    /**
     * The 403 of the coarse role filter, which is a denial with somebody behind it.
     *
     * <p>The line is asserted, but not its {@code accountId}: the session comes from a mutator, whose
     * principal is a plain {@code OAuth2User} rather than the {@code UkunOidcUser} a real login builds,
     * and the account id lives on that type alone. Where the caller's id is deposited —
     * {@code ProblemDetailSecurityHandler}, the only place that still knows it once the chain has
     * decided to stop — is asserted directly in {@code ProblemDetailSecurityHandlerTest}.
     */
    @Test
    @DisplayName("a 403 from the coarse role filter is logged too")
    void logsTheForbiddenDenial() {
        signedInWith(List.of("CUSTOMER"))
                .get()
                .uri(ADMIN_SURFACE)
                .exchange()
                .expectStatus()
                .isForbidden();

        awaitLineFor(ADMIN_SURFACE);

        assertThat(linesFor(ADMIN_SURFACE)).singleElement().satisfies(line -> assertSoftly(softly -> {
            softly.assertThat(line.getFormattedMessage()).contains("403");
            softly.assertThat(line.getLevel()).isEqualTo(Level.WARN);
        }));
    }

    /**
     * The line is written from {@code doFinally}, which fires when the exchange completes — and that
     * is not ordered against {@code expectStatus()} returning: the client can have the status in hand
     * while the outermost filter has not yet seen the completion signal. Asserting straight after the
     * call passed on its own and failed inside the full suite, which is the shape of a race, not of a
     * broken filter.
     *
     * <p>Awaitility and not a sleep, per {@code docs/13-testing.md}: it returns as soon as the line
     * lands, and the timeout is a failure mode rather than a fixed cost paid by every run.
     */
    private void awaitLineFor(String path) {
        await().atMost(Duration.ofSeconds(2)).until(() -> !linesFor(path).isEmpty());
    }

    private List<ILoggingEvent> linesFor(String path) {
        return lines.list.stream()
                .filter(line -> line.getFormattedMessage().contains(path))
                .toList();
    }
}
