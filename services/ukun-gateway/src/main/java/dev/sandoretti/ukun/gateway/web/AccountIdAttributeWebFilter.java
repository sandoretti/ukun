package dev.sandoretti.ukun.gateway.web;

import dev.sandoretti.ukun.gateway.security.UkunOidcUser;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Copies the caller's {@code accountId} onto the exchange so that {@link AccessLogWebFilter}, which
 * now runs <em>outside</em> the security chain, can still name the person.
 *
 * <h2>Why the access log cannot read the principal itself any more</h2>
 *
 * It used to, and that is precisely what confined it to successful requests. The principal is only
 * reachable from inside the security chain: Spring Security hands the filters below it a wrapped
 * exchange whose {@code getPrincipal()} reads the {@code SecurityContext} out of the Reactor context.
 * A filter placed before the chain holds the unwrapped exchange and would always see nobody — no
 * matter that it runs later in wall-clock time, because a Reactor context flows down the
 * subscription, not forward through it.
 *
 * <p>So the two needs pull in opposite directions: to log a denied request you must sit before the
 * security chain, and to know who was denied you must sit after it. An attribute is the one channel
 * that crosses that line, because the {@code ServerWebExchange} is the same object on both sides.
 *
 * <p>The 403 of the coarse role filter never reaches this filter — the chain rejects it above — so
 * the account of a request denied for lacking a role is deposited by {@code ProblemDetailSecurityHandler}
 * instead. That one is the only other place where a caller is known and the request stops.
 */
@Component
@Order(1)
class AccountIdAttributeWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return exchange.getPrincipal()
                .map(UkunOidcUser::accountIdOf)
                .doOnNext(accountId -> accountId.ifPresent(
                        id -> exchange.getAttributes().put(AccessLogAttributes.ACCOUNT_ID, id)))
                .then(chain.filter(exchange));
    }
}
