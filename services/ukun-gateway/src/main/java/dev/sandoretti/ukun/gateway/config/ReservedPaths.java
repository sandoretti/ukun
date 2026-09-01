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
 * {@code /actuator} and {@code /logout} are served by this service. A fallback that swallowed any of
 * them would turn a backend call into an HTML page, which is the failure mode this class exists to
 * prevent.
 *
 * <h2>{@code /logout} is a server path, so the SPA posts to it and does not navigate to it</h2>
 *
 * The logout endpoint of the security chain answers {@code POST /logout} only, and a filter handles
 * it before any mapping is consulted, so reserving the prefix changes nothing for the logout itself.
 * What it decides is the other half: {@code GET /logout} answers 404 instead of the HTML shell, which
 * means the frontend cannot own {@code /logout} as a client-side route. That is on purpose and
 * matches {@code /login} — a path this service owns should not have two meanings depending on the
 * verb, and a "signing out…" screen that lives at the same URL as the endpoint is the kind of thing
 * that works in development and breaks behind a bookmark.
 */
public final class ReservedPaths {

    private static final List<String> PREFIXES =
            List.of("/api", "/media", "/oauth2", "/login", "/logout", "/.well-known", "/actuator", "/internal");

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
        return PREFIXES.stream().anyMatch(prefix -> isUnder(path, prefix));
    }

    /**
     * The segment boundary on its own, for whoever cares about a single prefix rather than the whole
     * list — {@code RequestRateLimitWebFilter} meters {@code /api/**} and only that one. Written once
     * because {@code startsWith} alone is the bug, not the implementation: it captures {@code /apix}
     * too, and a second copy is how the two halves of this rule stop agreeing.
     */
    public static boolean isUnder(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }
}
