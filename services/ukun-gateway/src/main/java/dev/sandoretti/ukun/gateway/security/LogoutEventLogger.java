package dev.sandoretti.ukun.gateway.security;

import java.util.Map;
import org.slf4j.event.Level;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import reactor.core.publisher.Mono;

/**
 * Emits {@code auth.logout}, the one row of the closed registry in
 * {@code docs/11-observabilidad-y-logging.md} §3 whose emitter is this service. INFO, with the
 * {@code accountId} the table asks for.
 *
 * <p>It runs as a logout <em>handler</em> rather than as a listener, because the account id is only
 * reachable from the {@link Authentication} being torn down and WebFlux publishes no logout event
 * carrying it. The handler contract hands that {@code Authentication} in as an argument, which is
 * also why this does not have to run before the handler that invalidates the session: the principal
 * arrives by parameter, not by a lookup that could come too late.
 */
final class LogoutEventLogger implements ServerLogoutHandler {

    private static final String EVENT = "auth.logout";

    @Override
    public Mono<Void> logout(WebFilterExchange exchange, Authentication authentication) {
        return Mono.fromRunnable(() -> BusinessEvents.emit(Level.INFO, EVENT, Map.of("accountId", accountIdOf(authentication))));
    }

    /**
     * The {@code sub} of the access token, lifted into the principal by
     * {@link AccessTokenOidcUserService}. The fallback covers the shape this filter cannot rule out —
     * a logout arriving with some other authentication, or none — and says so rather than dropping
     * the event: an audit line that admits what it does not know beats a missing one.
     */
    private static String accountIdOf(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UkunOidcUser user) {
            return user.accountId();
        }
        return "unknown";
    }
}
