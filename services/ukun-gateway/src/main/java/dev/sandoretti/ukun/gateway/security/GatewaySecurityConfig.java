package dev.sandoretti.ukun.gateway.security;

import dev.sandoretti.ukun.gateway.config.UkunGatewayProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import static org.springframework.security.web.server.header.StrictTransportSecurityServerHttpHeadersWriter.STRICT_TRANSPORT_SECURITY;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy;
import org.springframework.security.web.server.header.StaticServerHttpHeadersWriter;

/**
 * The security chain of the single entry point: the coarse route filter of ADR-0020 and the four
 * headers of {@code docs/08-identidad-y-seguridad.md} §8, which the legacy nginx emitted none of.
 *
 * <h2>One rule for admin, not a list of routes</h2>
 *
 * ADR-0020 makes {@code /api/admin/**} a biconditional — every admin-only operation hangs off it and
 * nothing else does — and that is exactly what lets this be a single matcher instead of a table of
 * method+path pairs that ages with every endpoint. If adding a route ever means widening the
 * pattern, the route is in the wrong place.
 *
 * <p>This is <strong>defence in depth and never the only barrier</strong>. {@code ukun-api}
 * validates the JWT itself as a resource server and repeats every check with {@code @PreAuthorize},
 * trusting no header this gateway may have injected ({@code docs/08} §4). Resource-level
 * authorisation — "this order is mine", "this store is mine" — cannot be expressed as a path at all
 * and is not attempted here.
 *
 * <h2>What this chain deliberately does not do yet — issue #28</h2>
 *
 * No {@code oauth2Login}, no session, no {@code TokenRelay}, no {@code XSRF-TOKEN} cookie: that is
 * the BFF slice, and it is the one carrying the {@code revision-humana} label. Until it lands there
 * is no authentication mechanism at all, so {@code /api/admin/**} and {@code /api/me/**} answer 401
 * to everyone — which is the safe direction to be wrong in.
 *
 * <h3>Two things #28 inherits, and one it must not break</h3>
 *
 * The routes to {@code ukun-auth} declared in {@code application.yaml} cover {@code /oauth2/**} and
 * {@code /login/oauth2/**}, and {@code oauth2Login} will claim two paths inside them:
 * {@code /oauth2/authorization/{registrationId}} to start the flow and
 * {@code /login/oauth2/code/{registrationId}} to receive the callback. There is no conflict, and the
 * reason is structural rather than a matter of ordering that someone could quietly change: every
 * {@code WebFilter} runs before the first {@code HandlerMapping} is consulted, and Spring Security's
 * {@code WebFilterChainProxy} is {@code @Order(-100)}. A filter that terminates the exchange — the
 * 302 to the authorization endpoint, the callback — means {@code RoutePredicateHandlerMapping}
 * never sees the request. Everything else under {@code /oauth2/**} ({@code /oauth2/authorize},
 * {@code /oauth2/token}, {@code /oauth2/jwks}) passes through {@code permitAll} and gets routed.
 *
 * <p>What would break it is a {@code securityMatcher} narrowing this chain so those paths fall
 * outside it: the local filter would stop running and the 302 would be proxied to {@code ukun-auth}
 * instead. The chain must keep covering {@code anyExchange}.
 *
 * <p>(Also worth knowing: the {@code /login/oauth2/**} route to {@code ukun-auth} is, in practice,
 * dead — the only path that exists under it is the local callback, which wins. It is declared
 * because the routing table is meant to be read against the contract, not because that service
 * serves anything there.)
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
class GatewaySecurityConfig {

    /**
     * {@code docs/08} §8 asks for HSTS on every response. A year, subdomains included; no
     * {@code preload}, which is a submission to a browser-maintained list and not a header setting.
     */
    private static final String ONE_YEAR_WITH_SUBDOMAINS = "max-age=31536000 ; includeSubDomains";

    @Bean
    SecurityWebFilterChain gatewayFilterChain(
            ServerHttpSecurity http,
            UkunGatewayProperties properties,
            ProblemDetailSecurityHandler problemHandler) {

        return http.authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/admin/**").hasRole("ADMIN")
                        .pathMatchers("/api/me/**").authenticated()
                        // The public catalogue, /media/**, the SPA itself and /actuator/health.
                        // Not an oversight: what the coarse filter cannot decide, the API decides —
                        // and it is the authority anyway.
                        .anyExchange().permitAll())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(properties.contentSecurityPolicy()))
                        .contentTypeOptions(Customizer.withDefaults())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // The stock writer checks the request scheme and stays silent on plain HTTP,
                        // so under `tilt up` the header would never come out and "every response
                        // carries the four" would be false in the one environment anyone can check.
                        // Emitting it always is safe by RFC 6797: a user agent MUST ignore an STS
                        // header received over a non-secure transport. And it is the only version
                        // that survives TLS being terminated by something in front of us, which we
                        // cannot detect either — X-Forwarded-Proto is not read without a configured
                        // trusted proxy (skill `spring-gateway`), same as X-Forwarded-For.
                        //
                        // One header per writer, never four in one: StaticServerHttpHeadersWriter
                        // writes NONE of its headers if ANY of them is already present, and proxied
                        // responses from ukun-api arrive with `nosniff` already set.
                        .hsts(ServerHttpSecurity.HeaderSpec.HstsSpec::disable)
                        .writer(StaticServerHttpHeadersWriter.builder()
                                .header(STRICT_TRANSPORT_SECURITY, ONE_YEAR_WITH_SUBDOMAINS)
                                .build()))
                // Not a hole left open: CSRF protects a session, and this service has none until #28
                // gives it one. That slice replaces this line with the XSRF-TOKEN cookie repository
                // of ADR-0003, and until it does, leaving the default on would only mean every
                // proxied POST answering 403 for a threat that does not exist yet.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .build();
    }
}
