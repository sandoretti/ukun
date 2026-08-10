package dev.sandoretti.ukun.gateway.security;

import static org.springframework.security.web.server.header.StrictTransportSecurityServerHttpHeadersWriter.STRICT_TRANSPORT_SECURITY;

import dev.sandoretti.ukun.gateway.config.UkunGatewayProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.authentication.logout.WebSessionServerLogoutHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy;
import org.springframework.security.web.server.header.StaticServerHttpHeadersWriter;

/**
 * The security chain of the single entry point: the BFF of ADR-0003, the coarse route filter of
 * ADR-0020 and the four headers of {@code docs/08-identidad-y-seguridad.md} §8, which the legacy
 * nginx emitted none of.
 *
 * <h2>The BFF, in one paragraph</h2>
 *
 * The browser holds a session cookie and nothing else. This service runs the authorization code flow
 * with PKCE against {@code ukun-auth}, keeps the tokens server-side in that session, and attaches the
 * access token to what it proxies to {@code ukun-api} ({@code TokenRelay}, declared per route in
 * {@code application.yaml}). <strong>No response ever puts a token where JavaScript can reach it</strong>
 * — that is the entire point of the pattern, and the reason a token in an XSS is not a risk that has
 * to be mitigated but one that does not exist.
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
 * and is not attempted here. The roles it matches on come from the very token it relays, so the two
 * layers cannot disagree; see {@link AccessTokenOidcUserService}.
 *
 * <h2>Why the local endpoints win over the routes to ukun-auth</h2>
 *
 * The routes declared in {@code application.yaml} cover {@code /oauth2/**} and {@code /login/oauth2/**},
 * and {@code oauth2Login} claims two paths inside them: {@code /oauth2/authorization/ukun} to start
 * the flow and {@code /login/oauth2/code/ukun} to receive the callback. There is no conflict, and the
 * reason is structural rather than a matter of ordering that someone could quietly change: every
 * {@code WebFilter} runs before the first {@code HandlerMapping} is consulted, and Spring Security's
 * {@code WebFilterChainProxy} is {@code @Order(-100)}. A filter that terminates the exchange — the
 * 302 to the authorization endpoint, the callback — means {@code RoutePredicateHandlerMapping} never
 * sees the request. Everything else under {@code /oauth2/**} ({@code /oauth2/authorize},
 * {@code /oauth2/token}, {@code /oauth2/jwks}) passes through {@code permitAll} and gets routed.
 *
 * <p>What would break it is a {@code securityMatcher} narrowing this chain so those paths fall
 * outside it: the local filter would stop running and the 302 would be proxied to {@code ukun-auth}
 * instead. <strong>The chain must keep covering {@code anyExchange}.</strong>
 *
 * <h2>401 with a body, never a redirect to the login page</h2>
 *
 * {@link ProblemDetailSecurityHandler} stays the entry point now that there is something to log in
 * to, and that is deliberate. {@code docs/08} §6 removes server-side redirects from this system on
 * purpose — a REST API does not redirect, the frontend decides navigation — so an unauthenticated
 * {@code fetch} gets a {@code ProblemDetail} it can switch on, and the SPA navigates to
 * {@code /oauth2/authorization/ukun} itself when it decides to. That endpoint starts the flow
 * regardless of the entry point, so nothing is lost.
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
            UkunOAuth2Properties oauth2,
            ReactiveClientRegistrationRepository clients,
            ProblemDetailSecurityHandler problemHandler) {

        ServerCsrfTokenRepository csrfTokens = csrfTokenRepository();

        return http.authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/admin/**").hasRole("ADMIN")
                        .pathMatchers("/api/me/**").authenticated()
                        // The public catalogue, /media/**, the SPA itself and /actuator/health.
                        // Not an oversight: what the coarse filter cannot decide, the API decides —
                        // and it is the authority anyway.
                        .anyExchange().permitAll())
                .oauth2Login(Customizer.withDefaults())
                .logout(logout -> {
                    logout.logoutHandler(handlers -> {
                        // The list arrives holding SecurityContextServerLogoutHandler, so these are
                        // added to the default rather than replacing it. They run in order —
                        // DelegatingServerLogoutHandler concatMaps them — but nothing here depends on
                        // that: every handler is handed the same Authentication as an argument, so
                        // the event does not care that a later one destroys the session.
                        handlers.add(new LogoutEventLogger());
                        // Clearing the security context is not enough. The session also holds the
                        // OAuth2AuthorizedClient — the access and refresh tokens — and leaving it
                        // behind would mean logging out kept the credential. Invalidating drops both.
                        handlers.add(new WebSessionServerLogoutHandler());
                        // The XSRF-TOKEN cookie is expired too, but not from here: configuring the
                        // csrf() spec below already adds a CsrfServerLogoutHandler bound to the same
                        // repository. Adding one here as well is not harmless-but-redundant — it
                        // sends the deletion twice, as two identical Set-Cookie headers.
                    });
                    logout.logoutSuccessHandler(rpInitiatedLogout(clients, oauth2));
                })
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
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokens)
                        // CsrfWebFilter carries its own handler and does not consult the one in
                        // exceptionHandling below, so without this line a rejected mutation answers a
                        // bare 403 with no body — a status the frontend cannot tell apart from any
                        // other refusal, on the one failure it is most likely to hit while wiring up.
                        .accessDeniedHandler(problemHandler)
                        // Not decoration: the default is XorServerCsrfTokenRequestAttributeHandler,
                        // which BREACH-encodes the token it puts in the request attribute while the
                        // cookie keeps the raw one. A SPA reading the cookie and echoing it verbatim
                        // would then send a value the server un-XORs into noise, and every mutation
                        // would answer 403. The plain handler is what makes cookie and header the
                        // same string, which is the contract the frontend is written against.
                        .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieWebFilter(), SecurityWebFiltersOrder.CSRF)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandler)
                        // CsrfException is an AccessDeniedException, so a rejected mutation comes out
                        // as the FORBIDDEN of docs/12 §2 with no extra wiring — and without inventing
                        // a code, which that registry is closed against.
                        .accessDeniedHandler(problemHandler))
                .build();
    }

    /**
     * {@code HttpOnly=false} is the one attribute that has to be off, and it is not a weakening of
     * the design: this cookie is <em>meant</em> to be read by the SPA and echoed in
     * {@code X-XSRF-TOKEN}, which is what proves the request came from our own JavaScript rather than
     * from a form on someone else's page. The credential that must stay unreadable is the session
     * cookie, and that one keeps its {@code HttpOnly} (ADR-0003).
     *
     * <p>{@code SameSite=Lax} matches the session cookie, per {@code docs/08} §8. {@code Secure} is
     * deliberately not forced here: under {@code tilt up} the stack is plain HTTP and a hardcoded
     * {@code Secure} would make the cookie silently absent in the one environment anyone develops
     * against. It is set by configuration where the deployment is HTTPS.
     */
    private static ServerCsrfTokenRepository csrfTokenRepository() {
        CookieServerCsrfTokenRepository repository = CookieServerCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));

        return repository;
    }

    /**
     * RP-initiated logout: the browser is sent on to {@code ukun-auth} so the session <em>there</em>
     * ends too. Without it, logging out of the gateway and clicking login again would walk straight
     * back in without a password — the authorization server would still recognise the browser and
     * issue a fresh code without prompting.
     *
     * <p>The end session endpoint is read from the client registration's provider metadata, which
     * {@link ClientRegistrationConfig} fills in by hand because no discovery document is ever
     * fetched. The post-logout URI is passed literally, not as {@code {baseUrl}}: the authorization
     * server compares it against the registered value as a string, and {@code {baseUrl}} expands
     * without the trailing slash that registration carries.
     */
    private static ServerLogoutSuccessHandler rpInitiatedLogout(
            ReactiveClientRegistrationRepository clients, UkunOAuth2Properties oauth2) {

        OidcClientInitiatedServerLogoutSuccessHandler handler =
                new OidcClientInitiatedServerLogoutSuccessHandler(clients);
        handler.setPostLogoutRedirectUri(oauth2.postLogoutRedirectUri());

        return handler;
    }
}
