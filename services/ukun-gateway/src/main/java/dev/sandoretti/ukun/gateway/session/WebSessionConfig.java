package dev.sandoretti.ukun.gateway.session;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.session.config.annotation.web.server.EnableSpringWebSession;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.scheduler.Schedulers;

/**
 * Puts {@link JdbcWebSessionRepository} behind the {@code WebSessionManager} that WebFlux asks for
 * (ADR-0024).
 *
 * <p>{@code @EnableSpringWebSession} is declared here and not left to Boot on purpose: Boot 4's
 * reactive session autoconfiguration contributes the timeout and nothing else, so without this
 * annotation the repository below would be a bean nobody consults and every session would still live
 * in the heap — silently, which is the worst way for this to fail.
 *
 * <p>The cookie keeps coming from {@code server.reactive.session.cookie.*}: Spring Session's
 * configuration injects whatever {@code WebSessionIdResolver} is in the context, and Boot already
 * publishes one built from those properties. Nothing about {@code HttpOnly} or {@code SameSite}
 * (ADR-0003) changes here, and the cookie is still called {@code SESSION} — that is WebFlux's own
 * default name, so unlike {@code ukun-auth} this service's cookie does not change at all.
 */
@Configuration(proxyBeanMethods = false)
@EnableSpringWebSession
class WebSessionConfig {

    /**
     * Injected rather than read from {@code Instant.now()} inside the repository, so that a test can
     * move time instead of rewriting {@code expires_at} behind the repository's back — which would be
     * asserting that the table can be edited (skill {@code testing}).
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * {@code boundedElastic} and not the parallel scheduler: these calls block, and that is the one
     * scheduler Reactor sizes for work that does. Bounded matters — an unbounded pool in front of a
     * connection pool only moves the queue.
     */
    @Bean
    JdbcWebSessionRepository webSessionRepository(
            JdbcClient jdbc,
            TransactionTemplate transactions,
            Clock clock,
            @Value("${server.reactive.session.timeout}") Duration timeout) {

        return new JdbcWebSessionRepository(jdbc, transactions, Schedulers.boundedElastic(), clock, timeout);
    }
}
