package dev.sandoretti.ukun.gateway.web;

import dev.sandoretti.ukun.gateway.config.ReservedPaths;
import dev.sandoretti.ukun.gateway.error.ProblemDetailWriter;
import dev.sandoretti.ukun.gateway.web.RequestRateLimitBuckets.Tier;
import io.github.bucket4j.ConsumptionProbe;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * The rate limit of {@code docs/08-identidad-y-seguridad.md} §8, applied at the single entry point.
 *
 * <h2>Why only {@code /api/**}</h2>
 *
 * Because that is the surface {@code docs/12-error-contract.md} governs, and the answer this filter
 * gives is a {@code ProblemDetail} the SPA knows how to paint. Everything else the gateway serves is
 * deliberately outside: {@code /media/**} is dozens of product photos per page view, {@code /actuator}
 * is what the container probes for liveness, the SPA's own bundle is static, and {@code /oauth2/**}
 * is a top-level browser navigation that would be answered with raw JSON on screen — besides not
 * crossing this service at all in this deployment, since the browser follows
 * {@code UKUN_AUTH_ISSUER} straight to {@code ukun-auth}.
 *
 * <p>That last point is the one worth stating out loud, because the issue asks for "login and
 * signup": <strong>the login form never reaches this filter.</strong> It posts to {@code /login} on
 * {@code ukun-auth}'s own origin, and this gateway routes {@code /login/oauth2/**} and nothing else
 * under that prefix. The login budget therefore lives where the login does — {@code LoginRateLimiter}
 * in {@code ukun-auth} — and duplicating it here would be a limit on traffic that does not exist.
 * What this service sees of that pair is {@code POST /api/signup}, and that is what {@link Tier#SIGNUP}
 * is for.
 *
 * <h2>Why {@code @Order(2)}</h2>
 *
 * Two constraints, both inherited rather than invented. It must run <em>after</em> the security chain
 * ({@code @Order(-100)}) for the reason {@link RequestSizeLimitWebFilter} documents at
 * {@code @Order(0)}: the four security headers of {@code docs/08} §8 are written from inside that
 * chain, in a {@code beforeCommit} hook, so a 429 emitted in front of it would go out undressed. And
 * it must run after {@link AccountIdAttributeWebFilter} ({@code @Order(1)}), so that the access log
 * line of a throttled request still names the account behind it.
 *
 * <p>Being behind the size limit at {@code @Order(0)} has a visible consequence and it is the right
 * one: an oversized body is answered 413 without spending budget. Cutting a request on the cheapest
 * ground available is not a leak — the client learns nothing it did not already send.
 *
 * <h2>The key is the remote address, and that is a decision with an expiry date</h2>
 *
 * {@code X-Forwarded-For} is deliberately not read: it is a header whoever calls can set, and this
 * service has no trusted proxy configured — the same reasoning {@code LoginEventListener} already
 * records in {@code ukun-auth}. Behind a balancer that stops being a correct default and becomes a
 * configuration requirement, which is issue #118 point 5, together with moving these buckets to a
 * shared store. Neither half is worth having without the other.
 */
@Component
@Order(2)
class RequestRateLimitWebFilter implements WebFilter {

    private static final String API_PREFIX = "/api";
    private static final String SIGNUP_PATH = "/api/signup";

    /** No address on the exchange means a connection this service cannot tell apart from any other. */
    private static final String UNKNOWN_CLIENT = "unknown";

    private final RequestRateLimitBuckets buckets;
    private final ProblemDetailWriter problems;

    RequestRateLimitWebFilter(RequestRateLimitBuckets buckets, ProblemDetailWriter problems) {
        this.buckets = buckets;
        this.problems = problems;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        Tier tier = tierOf(request);

        if (tier == null) {
            return chain.filter(exchange);
        }

        ConsumptionProbe probe =
                buckets.forClient(tier, clientOf(request)).tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            return chain.filter(exchange);
        }

        // Rounded up to at least a second: `Retry-After: 0` invites the client to come straight back
        // and be refused again, which is the busy loop this filter exists to stop.
        long secondsToWait = Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds();

        return problems.rateLimited(exchange, Math.max(1, secondsToWait));
    }

    /** {@code null} for a path this filter leaves alone. */
    private static Tier tierOf(ServerHttpRequest request) {
        String path = request.getPath().value();

        // Not `startsWith` on its own: /apix is a client-side route of the SPA and metering it would
        // be metering a path this service does not even route. ReservedPaths owns that boundary.
        if (!ReservedPaths.isUnder(path, API_PREFIX)) {
            return null;
        }
        return HttpMethod.POST.equals(request.getMethod()) && SIGNUP_PATH.equals(path) ? Tier.SIGNUP : Tier.API;
    }

    private static String clientOf(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();

        return remote == null || remote.getAddress() == null
                ? UNKNOWN_CLIENT
                : remote.getAddress().getHostAddress();
    }
}
