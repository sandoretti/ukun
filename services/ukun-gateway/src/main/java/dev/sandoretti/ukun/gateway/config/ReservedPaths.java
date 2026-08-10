package dev.sandoretti.ukun.gateway.config;

import java.util.List;

/**
 * The prefixes the SPA fallback must never answer. Everything else is a client-side route and gets
 * {@code index.html}, because the router lives in the browser.
 *
 * <h2>Why this list decides two things at once</h2>
 *
 * It is consumed by the static fallback ({@code SpaFallbackConfig}) <em>and</em> by the development
 * route that proxies to Vite ({@code DevSpaRouteConfig}), so an unknown path behaves the same in
 * both. Without that, {@code /internal/accounts} would be a 404 under {@code tilt up} and an
 * {@code index.html} in production, or the other way round — and the test that fixes the 404 would
 * only prove it for whichever mode the test happens to run in.
 *
 * <h2>{@code /internal} is the load-bearing entry</h2>
 *
 * {@code ukun-auth} publishes {@code /internal/accounts} and its {@code :activate}/{@code :release}
 * siblings for the signup saga, and only {@code ukun-api} may call them, from inside the container
 * network ({@code docs/08-identidad-y-seguridad.md} §3). There is no route to them here — that is
 * the design, not an omission. Listing the prefix here is what stops the SPA fallback from turning
 * "no route" into a friendly {@code 200} with an HTML page: no route, no static resource and no
 * fallback means no handler at all, and {@code DispatcherHandler} answers 404.
 *
 * <p>The others are routed or handled locally: {@code /api} and {@code /media} go to
 * {@code ukun-api}; {@code /oauth2}, {@code /login} and {@code /.well-known} to {@code ukun-auth}
 * (some of them intercepted first by the security chain — see {@code GatewaySecurityConfig});
 * {@code /actuator} is served by this service. A fallback that swallowed any of them would turn a
 * backend call into an HTML page, which is the failure mode this class exists to prevent.
 */
public final class ReservedPaths {

    private static final List<String> PREFIXES =
            List.of("/api", "/media", "/oauth2", "/login", "/.well-known", "/actuator", "/internal");

    private ReservedPaths() {}

    /** The prefixes themselves, for a route predicate that has to name them one by one. */
    public static List<String> prefixes() {
        return PREFIXES;
    }

    /**
     * Whether {@code path} belongs to a reserved prefix. The segment boundary matters: {@code /apix}
     * is a perfectly good client-side route and must not be captured by {@code /api}.
     */
    public static boolean covers(String path) {
        return PREFIXES.stream().anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }
}
