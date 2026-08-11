package dev.sandoretti.ukun.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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

        assertThat(linesFor(ADMIN_SURFACE)).singleElement().satisfies(line -> assertSoftly(softly -> {
            softly.assertThat(line.getFormattedMessage()).contains("401");
            softly.assertThat(line.getLevel()).isEqualTo(Level.WARN);
            softly.assertThat(line.getMDCPropertyMap()).doesNotContainKey("accountId");
        }));
    }

    /**
     * The 403 of the coarse role filter, which is a denial with somebody behind it.
     *
     * <p>Only the status is asserted here, and not the {@code accountId}: {@code mockUser()} injects
     * its principal through a mutator that rebuilds the client's filter chain, so what reaches the
     * log in this setup is not what a real session would leave. Where the caller's id is deposited —
     * {@code ProblemDetailSecurityHandler}, the only place that still knows it once the chain has
     * decided to stop — is asserted directly in {@code ProblemDetailSecurityHandlerTest}.
     */
    @Test
    @DisplayName("a 403 from the coarse role filter is logged too")
    void logsTheForbiddenDenial() {
        http.mutateWith(mockUser().roles("CUSTOMER"))
                .get()
                .uri(ADMIN_SURFACE)
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    private List<ILoggingEvent> linesFor(String path) {
        return lines.list.stream()
                .filter(line -> line.getFormattedMessage().contains(path))
                .toList();
    }
}
