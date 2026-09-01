package dev.sandoretti.ukun.gateway.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Everything about this gateway that changes between environments, in one place.
 *
 * <h2>Why the destinations are properties and not literals</h2>
 *
 * {@code compose.yaml} brings the services up by container name, and every worktree shifts the
 * published ports ({@code .env.ports}, skill {@code worktree}), so a literal here would work in
 * exactly one checkout. The pattern is the one {@code ukun-api} already uses for
 * {@code UKUN_AUTH_JWK_SET_URI}: a property whose default names the internal service, overridable by
 * environment variable. The integration tests lean on the same seam, pointing the two backends at a
 * stub on an ephemeral port.
 *
 * <p>Note that these are the names <em>inside</em> the container network, never what a browser
 * resolves — unlike {@code UKUN_AUTH_ISSUER} in {@code ukun-auth}, which is a literal string the
 * browser must be able to follow. Confusing the two is the trap that slice documented, and it does
 * not apply here: nothing in this class is ever sent to a client.
 */
@ConfigurationProperties(prefix = "ukun.gateway")
public record UkunGatewayProperties(
        /** {@code /api/**} and {@code /media/**}. The second is routed, never served here — {@code docs/08} §9. */
        URI apiUri,

        /** {@code /oauth2/**}, {@code /login/oauth2/**} and {@code /.well-known/**}. */
        URI authUri,

        /** Vite's dev server. Only the {@code dev} profile routes to it ({@code DevSpaRouteConfig}). */
        URI webUri,

        /**
         * Where the built SPA lives, as a Spring resource location. Nothing packages the Vite bundle
         * into this service's image yet — that needs a build stage over {@code apps/ukun-web} and
         * belongs to the deployment slice — so the default points at where the image will mount it
         * and the fallback simply answers 404 until something is there.
         */
        String webRoot,

        /**
         * The same value as {@code spring.servlet.multipart.max-file-size} in {@code ukun-api}, and
         * it has to stay that way: defect #17 of {@code docs/03-analisis-y-patrones.md} §1 is
         * precisely that Spring said 10 MB, nginx declared no {@code client_max_body_size}, and the
         * effective limit was neither of the two.
         */
        DataSize maxBodySize,

        /**
         * {@code docs/08-identidad-y-seguridad.md} §8. It is the only one of the four security
         * headers that is configuration rather than a constant, because the {@code dev} profile has
         * to relax it for Vite's dev server; the other three have one correct value and live in
         * {@code GatewaySecurityConfig}.
         */
        String contentSecurityPolicy,

        /**
         * The budgets of the rate limiter, {@code docs/08-identidad-y-seguridad.md} §8. They are
         * configuration rather than constants for one reason and it is not tuning: an integration
         * test that has to run out of budget cannot afford the production numbers, and a limiter
         * whose only test is a unit test is a limiter nobody has seen answer over the wire.
         */
        RateLimit rateLimit) {

    /**
     * Two budgets, because one cannot be both lenient and hostile — the same shape as the two
     * bandwidths of {@code LoginAttemptBuckets} in {@code ukun-auth}, for the same reason.
     */
    public record RateLimit(
            /** Everything under {@code /api/**}. Generous: a person browsing the shop must never see it. */
            Tier api,

            /**
             * {@code POST /api/signup} alone. The half of "login and signup" that this service sees:
             * the login form posts to {@code ukun-auth}'s own origin and never crosses the gateway,
             * which is why the login budget lives there and is not duplicated here.
             */
            Tier signup) {

        /** A capacity that refills whole, once per window — Bucket4j's {@code refillIntervally}. */
        public record Tier(int capacity, Duration window) {}
    }
}
