package dev.sandoretti.ukun.api.identity.config;

import static org.springframework.security.config.Customizer.withDefaults;

import tools.jackson.databind.ObjectMapper;
import dev.sandoretti.ukun.api.shared.observability.AccountIdMdcFilter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * This service as an OAuth2 resource server: it validates the token ukun-auth issued, by itself,
 * against that service's JWKS.
 *
 * <p><strong>By itself</strong> is the load-bearing part. ukun-gateway also checks a coarse rule per
 * route, but that is defence in depth, not the barrier — this service is the final authority and
 * never trusts a header the gateway may have injected ({@code docs/08-identidad-y-seguridad.md} §4).
 * A request that reaches port 8081 directly is subject to exactly the same rules as one that came
 * through the gateway, which is what makes the gateway's own rules an optimisation rather than a
 * secret.
 *
 * <p>How a token is validated — the JWKS to fetch, the literal {@code iss} to compare against, the
 * {@code aud} that must be ours and the {@code roles} claim that becomes {@code ROLE_*} — is
 * configured in {@code application.yaml}, where the reasoning for each line lives. This class decides
 * only <em>which routes</em> need one.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    /**
     * The eight operations the contract marks {@code security: []} in
     * {@code docs/api/openapi/ukun-api.yaml}. They are listed by hand, one line per operation, rather
     * than collapsed into {@code /api/**} patterns: this list <em>is</em> the public surface of the
     * service, and it should be readable against the YAML without inference.
     *
     * <p>Note what is <strong>not</strong> here. {@code /api/stores} is public but
     * {@code /api/stores/{storeId}/stock} is not, so the pattern is exact and not {@code /api/stores/**}
     * — a single character would hand every employee's stock to anonymous callers.
     */
    private static final String[] PUBLIC_GET = {
        "/api/products",
        "/api/products/*",
        "/api/products/by-sku/*",
        "/api/product-types",
        "/api/availability",
        "/api/stores",
    };

    /**
     * Public because the pictures are: an {@code <img>} tag carries no token, and this route serves
     * static content that is deliberately not an operation of the contract ({@code docs/08} §9).
     */
    private static final String MEDIA = "/media/**";

    /**
     * Signing up cannot require being signed in. The endpoint does not exist yet — it arrives with the
     * saga — and it is declared here anyway so that slice does not have to come back and edit a
     * security configuration to add a route the contract already froze as public.
     */
    private static final String SIGNUP = "/api/signup";

    /**
     * Left open for two consumers that cannot present a token and are not a security decision:
     *
     * <ul>
     *   <li>{@code /actuator/health}, polled by the container's {@code HEALTHCHECK} — close it and the
     *       container reports unhealthy, which stalls the {@code depends_on: service_healthy} of
     *       {@code compose.dev.yaml} and with it {@code tilt up}.
     *   <li>{@code /v3/api-docs} and Swagger UI, which seven integration tests read to diff the
     *       generated document against the frozen contract. What keeps these private in production is
     *       {@code management.endpoints.web.exposure} and {@code springdoc.*.enabled}, both off outside
     *       the {@code dev} profile — the filter chain is not what was protecting them.
     * </ul>
     */
    private static final String[] OPERATIONAL = {
        "/actuator/health",
        "/actuator/health/**",
        "/actuator/modulith",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
    };

    @Bean
    SecurityFilterChain apiFilterChain(HttpSecurity http, ProblemDetailAuthenticationHandler problemHandler)
            throws Exception {

        return http.authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, MEDIA)
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, SIGNUP)
                        .permitAll()
                        .requestMatchers(OPERATIONAL)
                        .permitAll()
                        // Deny by default. Everything the contract does not mark `security: []` needs a
                        // token, including routes nobody has written yet — a new endpoint is protected
                        // the moment it exists, instead of protected the moment somebody remembers.
                        .anyRequest()
                        .authenticated())
                // The two handlers are registered here and nowhere else. Declaring them again through
                // `exceptionHandling(...)` is redundant, not belt-and-braces: this call installs them as
                // the chain's, so the 401 of a rejected token and the 403 of a denied route already come
                // out of the same pair. Measured by removing the second block and watching the nine tests
                // of both paths stay green.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults())
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                // Right after authentication and before authorisation, which is the only window where
                // both halves are true: there is a validated token to read the subject from, and the
                // request has not been denied yet — so `auth.authorization.denied` still gets logged
                // with the `accountId` that docs/11 §3 says it carries. Registering it any later would
                // name every request except the interesting ones.
                .addFilterAfter(new AccountIdMdcFilter(), BearerTokenAuthenticationFilter.class)
                // No session, ever: this service authenticates each request from its Bearer token and
                // keeps nothing between them. The session lives in the gateway, which is the only part
                // of the system talking to a browser (ADR-0003).
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF is a defence against a browser attaching credentials it stores by itself —
                // cookies. There are none here. The `XSRF-TOKEN` that protects the browser's session is
                // the gateway's job (`docs/08` §6 and §8); duplicating it on a stateless Bearer API
                // would reject every legitimate service-to-service call for no gain.
                .csrf(csrf -> csrf.disable())
                .build();
    }

    /** The two writers of {@code 401}/{@code 403} inside the filter chain. */
    @Bean
    ProblemDetailAuthenticationHandler problemDetailAuthenticationHandler(ObjectMapper objectMapper) {
        return new ProblemDetailAuthenticationHandler(objectMapper);
    }

    /**
     * Turns every authorisation decision into an application event. Spring Security publishes nothing
     * unless this bean exists — without it {@code AuthorizationDeniedListener} compiles, registers,
     * and is never called, which is the quietest possible way to lose an audit trail
     * ({@code docs/11-observabilidad-y-logging.md} §3).
     */
    @Bean
    AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher publisher) {
        return new SpringAuthorizationEventPublisher(publisher);
    }
}
