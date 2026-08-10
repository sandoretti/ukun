package dev.sandoretti.ukun.auth.config;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
// Same Spring Security 7 move as the configurer in AuthorizationServerConfig: this class left the
// authorization-server artifact for spring-security-config, under the package the rest of the web
// configuration already lived in. Examples written against 6.x import it from
// ...oauth2.server.authorization.config.annotation.web.configuration, which no longer exists.
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

/**
 * The chain that guards {@code /internal/**}, the service-to-service surface of
 * {@code docs/api/openapi/ukun-auth.yaml}.
 *
 * <h2>This service is a resource server for its own tokens</h2>
 *
 * There is a symmetry worth naming: ukun-auth issues the token and then validates it here, exactly as
 * ukun-api does with the same tokens (issue #24). The decoder is built from the very {@link JWKSource}
 * the signing key lives in, so no HTTP call to a JWKS endpoint is involved — the key is already in
 * this process. What that buys is that {@code /internal/accounts} is protected by the same mechanism
 * as everything else, rather than by a shared secret or an "it is only reachable inside the network"
 * assumption. A request arriving at port 9000 from anywhere is subject to the same rule.
 *
 * <h2>Order, and what happens when it is wrong</h2>
 *
 * Three chains now, and this one has to sit between the other two:
 *
 * <ol>
 *   <li>{@code HIGHEST_PRECEDENCE} — the Authorization Server's own endpoints.
 *   <li>{@code +1} — <strong>this one</strong>, matched on {@code /internal/**}.
 *   <li>{@code +2} — everything else: the login form and the health probe.
 * </ol>
 *
 * <p>Put this after the catch-all and the symptom is not an obvious failure: the third chain matches
 * {@code /internal/accounts} first, decides it needs a form login, and answers a {@code 302} to
 * {@code /login}. ukun-api's {@code RestClient} then reports an unexpected redirect rather than a
 * {@code 401}, several layers from the cause.
 *
 * <p>{@code accounts:write} is required for all three operations, as rule 5 of the {@code spring-auth}
 * skill states. Only the {@code ukun-api} client holds that scope; the gateway's client does not, so
 * a token minted for the browser's session cannot reach these endpoints even if one were somehow
 * presented here.
 */
@Configuration(proxyBeanMethods = false)
class InternalApiSecurityConfig {

    /** The prefix of the internal contract. Nothing else in this service lives under it. */
    private static final String INTERNAL = "/internal/**";

    /**
     * The scope, spelled as Spring Security grants it: a {@code scope} claim becomes one authority per
     * value, prefixed with {@code SCOPE_}. Written as an authority and not with
     * {@code hasAuthority("SCOPE_" + …)} assembled at runtime, so the string that matters is greppable.
     */
    private static final String ACCOUNTS_WRITE = "SCOPE_accounts:write";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    SecurityFilterChain internalApiFilterChain(HttpSecurity http, JwtDecoder jwtDecoder, ObjectMapper objectMapper)
            throws Exception {

        ProblemDetailAuthenticationHandler problemHandler = new ProblemDetailAuthenticationHandler(objectMapper);

        return http.securityMatcher(INTERNAL)
                .authorizeHttpRequests(requests ->
                        requests.anyRequest().hasAuthority(ACCOUNTS_WRITE))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                // No session: each call authenticates from its own Bearer token and nothing is kept
                // between them. The only session in this service belongs to the browser typing its
                // password into the login form, and it has no business being extended to a client
                // credentials call.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF defends against a browser attaching credentials it stores by itself. There are
                // none here — no cookie, no browser — and leaving it on would reject every legitimate
                // service-to-service POST, which is all three of these operations.
                .csrf(csrf -> csrf.disable())
                .build();
    }

    /**
     * Validates the tokens this service signs, from the key it signs them with.
     *
     * <p>{@code @Bean} rather than inline in the chain because Boot would otherwise try to
     * autoconfigure one from {@code spring.security.oauth2.resourceserver.jwt.*} — properties this
     * service has no reason to set, pointing at a JWKS endpoint it would then call over the network
     * to fetch its own public key.
     */
    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }
}
