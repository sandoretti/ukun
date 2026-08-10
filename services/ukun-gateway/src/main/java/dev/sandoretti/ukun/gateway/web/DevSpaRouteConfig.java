package dev.sandoretti.ukun.gateway.web;

import dev.sandoretti.ukun.gateway.config.ReservedPaths;
import dev.sandoretti.ukun.gateway.config.UkunGatewayProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.server.ServerWebExchange;

/**
 * Under {@code tilt up}, everything that is not a backend prefix goes to Vite's dev server, so that
 * {@code http://localhost:$GATEWAY_PORT} serves the whole application — the gateway as the single
 * entry point in development too, which is what {@code docs/06-entorno-local-tilt.md} describes.
 * In production this route does not exist and {@code SpaFallbackConfig} serves the built bundle
 * instead (skill {@code spring-gateway} §6).
 *
 * <h2>Why in Java and not in {@code application.yaml}</h2>
 *
 * A list under {@code spring.config.activate.on-profile} <em>replaces</em> the one in the default
 * document rather than adding to it, so declaring this route in a {@code dev} document would delete
 * the five backend routes. Beans of type {@link RouteLocator}, on the other hand, are composed into
 * a {@code CompositeRouteLocator}, so this one adds itself.
 *
 * <h2>Two details without which this does not work</h2>
 *
 * <ul>
 *   <li><strong>{@code preserveHostHeader()}.</strong> The routing filter rewrites {@code Host} to
 *       the destination unless told otherwise, and Vite ≥6 rejects any {@code Host} that is not
 *       {@code localhost} or an IP with <em>«Blocked request»</em> unless it is listed in
 *       {@code server.allowedHosts}. {@code apps/ukun-web/vite.config.ts} does not list
 *       {@code ukun-web}, and that file belongs to another track (issue #29). Keeping the browser's
 *       own {@code Host} sidesteps it, and is what a reverse proxy should be doing anyway.
 *   <li><strong>The order.</strong> The routes in the YAML carry the default order, so this one gets
 *       a higher number to stay behind them. It is belt and braces: the predicate already refuses
 *       the reserved prefixes, which is also what keeps {@code /internal/**} answering 404 here
 *       exactly as it does in production ({@link ReservedPaths}).
 * </ul>
 *
 * <p>HMR needs no configuration: the websocket upgrade is handled by the gateway's own
 * {@code WebsocketRoutingFilter}, which rewrites an {@code http} destination to {@code ws} when the
 * request carries {@code Upgrade: websocket}. And because {@code vite.config.ts} sets no
 * {@code server.hmr}, the injected client derives its socket URL from the page's own origin — the
 * gateway's — which is the behaviour meant for running behind a proxy.
 */
@Configuration(proxyBeanMethods = false)
@Profile("dev")
class DevSpaRouteConfig {

    /** Behind the routes declared in {@code application.yaml}, which use the default order. */
    private static final int AFTER_THE_BACKEND_ROUTES = 1;

    @Bean
    RouteLocator devSpaRoute(RouteLocatorBuilder routes, UkunGatewayProperties properties) {
        return routes.routes()
                .route("web-dev", route -> route
                        .order(AFTER_THE_BACKEND_ROUTES)
                        .predicate(DevSpaRouteConfig::isClientSideRoute)
                        .filters(filters -> filters.preserveHostHeader())
                        .uri(properties.webUri()))
                .build();
    }

    private static boolean isClientSideRoute(ServerWebExchange exchange) {
        return !ReservedPaths.covers(exchange.getRequest().getPath().value());
    }
}
