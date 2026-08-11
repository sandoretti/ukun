package dev.sandoretti.ukun.gateway.web;

import dev.sandoretti.ukun.gateway.security.UkunOidcUser;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * One line per request: method, path, status and duration, emitted here and only here
 * ({@code docs/11-observabilidad-y-logging.md} §6, last row). {@code ukun-api} and {@code ukun-auth}
 * do not repeat it — a single point of truth for traffic is the reason it belongs to the single point
 * of entry.
 *
 * <p>It is the line that makes this release's {@code traceId} visible from the gateway's side. Every
 * message carries the trace through Boot's correlation pattern, so a request that crosses three
 * processes now has one line here, one in {@code ukun-auth} and one in {@code ukun-api}, all three
 * under the same identifier. Without this filter the gateway would frequently log <em>nothing at all</em>
 * for a successful request, and "the same trace in the logs of the three services" would be
 * unverifiable for the one service that sees every request.
 *
 * <h2>What it must never contain</h2>
 *
 * No body, no {@code Authorization}, no {@code Cookie} ({@code docs/11} §4, point 4, which names
 * {@code CommonsRequestLoggingFilter} with payload as the thing not to build). The query string is
 * left out for the same reason it is never missed: the path already says which operation ran, and
 * whoever needs the filters of a catalogue query reads them from {@code ukun-api}, which is the
 * service that acted on them.
 *
 * <p>The person is named by {@code accountId} and never by {@code Principal#getName()}, which in this
 * service is the <em>email</em> — see {@link UkunOidcUser}. Putting it here would leak PII onto every
 * line of every request, which is the exact trade {@code docs/11} §5 refuses.
 *
 * <h2>Not a business event</h2>
 *
 * Its own logger, {@code ukun.access}, deliberately separate from {@code ukun.business}. This is not a
 * row of the closed registry of {@code docs/11} §3 — that table lists <em>decisions</em>, and an HTTP
 * exchange is not one. Keeping them apart is what lets production run {@code ukun.business} at INFO
 * without also keeping every proxied request.
 *
 * <h2>{@code @Order(-101)} — in front of everything, including security</h2>
 *
 * The security chain is {@code @Order(-100)} and {@link RequestSizeLimitWebFilter} is {@code @Order(0)}.
 * This filter used to run behind both, at {@code @Order(1)}, so that it could read the principal — and
 * the price, unnoticed until somebody went looking for a denied request in the logs, was that it never
 * saw one. A chain that rejects a request writes the response and completes without calling the
 * filters below it, so every 401 and every 403 of the coarse route filter passed through the single
 * point of entry leaving no line at all. A scan of protected routes was invisible in the one service
 * that sees every request.
 *
 * <p>Sitting in front is what makes "one line per request" true for requests that were refused. The
 * status is still the final one: it is read in {@code doFinally}, when the exchange is over and
 * whoever wrote the response — the security chain above, {@link RequestSizeLimitWebFilter}, or
 * {@code ukun-api} at the far end — has already set it.
 *
 * <p>What moving out costs is the principal, which is only reachable inside the chain. It arrives
 * instead as an exchange attribute left by {@link AccountIdAttributeWebFilter}.
 */
@Component
@Order(-101)
class AccessLogWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger("ukun.access");

    private static final String ACCOUNT_ID = "accountId";

    /** What is printed when the exchange ended before a status was assigned. */
    private static final String UNKNOWN_STATUS = "-";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long startedAt = System.nanoTime();

        // Read in `doFinally` and not here: at this point nobody has authenticated yet, and the whole
        // reason this filter sits in front of the security chain is to outlive the requests that never
        // get past it. By the time the exchange completes, whoever knew the caller has left it on the
        // exchange — AccountIdAttributeWebFilter for a request that went through, and
        // ProblemDetailSecurityHandler for one the chain refused.
        //
        // Absence stays an Optional all the way to `emit` instead of collapsing into a sentinel
        // string: there are two ways to have no account here — an anonymous request and a principal
        // this service does not recognise — and neither is an account whose id happens to be empty.
        return chain.filter(exchange).doFinally(signal -> emit(exchange, startedAt, signal));
    }

    private static void emit(ServerWebExchange exchange, long startedAt, SignalType signal) {

        Optional<String> accountId =
                Optional.ofNullable(exchange.getAttribute(AccessLogAttributes.ACCOUNT_ID));
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        long millis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        accountId.ifPresent(id -> MDC.put(ACCOUNT_ID, id));

        // Put, log and remove in one synchronous block with no operator in between, so no signal can
        // hop threads while the key is set. It is the same narrow guarantee the `BusinessEvents` of
        // this service documents, and the reason neither class ever leaves a value behind on an
        // event-loop thread where it would resurface attached to somebody else's request.
        try {
            log.atLevel(levelOf(status, signal))
                    .log(
                            "{} {} {} {}ms",
                            exchange.getRequest().getMethod(),
                            exchange.getRequest().getPath().value(),
                            status != null ? status.value() : UNKNOWN_STATUS,
                            millis);
        } finally {
            MDC.remove(ACCOUNT_ID);
        }
    }

    /**
     * The mechanical rule of {@code docs/11} §7, applied in one place instead of by each handler: a
     * {@code 4xx} is never ERROR — somebody asked for something they could not have, and the system
     * answered correctly — and a {@code 5xx} always is.
     *
     * <p>The status can still be absent, and the two ways that happens are not the same event. A
     * cancelled exchange is a browser that navigated away mid-request, which is INFO and no more; an
     * error signal that reached this filter without anyone writing a status is a failure that got past
     * every handler, and that is the definition of ERROR in this project.
     */
    private static Level levelOf(HttpStatusCode status, SignalType signal) {
        if (status == null) {
            return signal == SignalType.ON_ERROR ? Level.ERROR : Level.INFO;
        }
        if (status.is5xxServerError()) {
            return Level.ERROR;
        }
        return status.is4xxClientError() ? Level.WARN : Level.INFO;
    }
}
