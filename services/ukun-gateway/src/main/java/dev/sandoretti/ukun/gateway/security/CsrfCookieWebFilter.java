package dev.sandoretti.ukun.gateway.security;

import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Makes the {@code XSRF-TOKEN} cookie actually come out.
 *
 * <h2>The problem this exists for</h2>
 *
 * {@code CsrfWebFilter} does not write the token: it puts a <em>lazy</em> {@code Mono<CsrfToken>} in
 * an exchange attribute, and the repository only generates and saves — that is, only sets the
 * cookie — when something subscribes to it. In a server-rendered application the view renders the
 * token and subscribes as a side effect. Here nothing ever reads it: this gateway proxies and serves
 * a bundle, so without this filter the attribute is created and dropped on every request, the cookie
 * is never sent, and the SPA has no token to echo back. Every mutation would then answer 403 —
 * correctly, and for a reason nobody could see from the outside.
 *
 * <p>Registered inside the security chain right after {@code CsrfWebFilter}
 * ({@code SecurityWebFiltersOrder.CSRF}) rather than as a {@code @Component}: the position relative
 * to the filter that sets the attribute is the whole point, and a bean would also be picked up into
 * the global {@code WebFilter} chain, where that relationship is not expressed anywhere.
 */
final class CsrfCookieWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Mono<CsrfToken> token = exchange.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            return chain.filter(exchange);
        }

        // Subscribed before the chain continues, so the Set-Cookie is in place well before anything
        // downstream — the routing filter, the SPA fallback — commits the response.
        return token.then(chain.filter(exchange));
    }
}
