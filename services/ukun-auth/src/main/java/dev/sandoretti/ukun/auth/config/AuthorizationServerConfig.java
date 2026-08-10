package dev.sandoretti.ukun.auth.config;

import dev.sandoretti.ukun.auth.security.LoginAttemptBuckets;
import dev.sandoretti.ukun.auth.security.LoginRateLimiter;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
// Spring Security 7 moved the configurer out of the authorization-server artifact and into
// spring-security-config, under the package every other configurer already lived in. The old
// coordinates (...oauth2.server.authorization.config.annotation.web.configurers) simply do not
// exist any more, so any example written against 6.x fails to compile rather than misbehaving.
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * The Authorization Server, wired by hand rather than left to Boot's autoconfiguration.
 *
 * <p>Boot would provide equivalent beans silently, and for the two things this file actually
 * decides — that clients and authorizations are persisted, and that OIDC discovery is on — silence
 * is the wrong default: an in-memory {@code RegisteredClientRepository} works perfectly in
 * development and loses every client and every granted consent on restart.
 *
 * <p>Two filter chains, in the order Spring Security's own reference arrangement uses:
 *
 * <ol>
 *   <li>the Authorization Server's own endpoints ({@code /oauth2/authorize}, {@code /oauth2/token},
 *       the JWKS, the discovery document), matched by the configurer itself;
 *   <li>everything else.
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
class AuthorizationServerConfig {

    /** Where an unauthenticated browser is sent, and the path the rate limiter guards. */
    private static final String LOGIN_PATH = "/login";

    /**
     * The OAuth2/OIDC endpoints. They are deliberately not described in
     * {@code docs/api/openapi/ukun-auth.yaml}: they are standard and the server publishes its own
     * {@code /.well-known/openid-configuration}, so a hand-written copy would only drift.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain authorizationServerFilterChain(HttpSecurity http) throws Exception {
        // Constructed, not obtained from a static `authorizationServer()` factory: that shortcut
        // existed in Spring Security 6.x and is gone in 7, which is the second half of the same
        // move as the package change above.
        OAuth2AuthorizationServerConfigurer authorizationServer = new OAuth2AuthorizationServerConfigurer();

        http.securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(authorizationServer, server -> server.oidc(Customizer.withDefaults()))
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                // Without this, an unauthenticated browser arriving at /oauth2/authorize gets a 401
                // and the authorization code flow simply stops. The media type matcher is what keeps
                // a machine client (the token endpoint, a health probe) getting its 401 instead of a
                // redirect to an HTML page it cannot read.
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint(LOGIN_PATH),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));

        return http.build();
    }

    /**
     * Everything that is neither an Authorization Server endpoint nor part of the internal contract:
     * the login form, and the health probe that compose's {@code HEALTHCHECK} calls without
     * credentials.
     *
     * <p>Its order is {@code +2} and not {@code +1} because {@link InternalApiSecurityConfig} sits
     * between the two. This chain matches everything, so whatever comes after it is unreachable —
     * moving it back up would silently send {@code /internal/accounts} to the login form.
     *
     * <p>The generated login page is the real login UI, and stays so past v0.5: under the BFF of
     * ADR-0003 the browser is redirected <em>here</em> to type its password, and the SPA never sees
     * the credentials any more than it sees the token. Replacing it with a branded page is a v1.0
     * concern, not a missing piece of this one.
     *
     * <p>The rate limiter goes in front of the authentication filter rather than reacting to a
     * failure event, because the point is to stop the attempt before it costs a BCrypt verification —
     * which is deliberately expensive, and is therefore the thing an attacker would like to make us
     * do repeatedly.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 2)
    SecurityFilterChain defaultFilterChain(HttpSecurity http, LoginAttemptBuckets buckets) throws Exception {
        http.authorizeHttpRequests(requests -> requests.requestMatchers("/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .formLogin(Customizer.withDefaults())
                .addFilterBefore(new LoginRateLimiter(buckets), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Clients in {@code oauth2_registered_client}, whose columns are in the baseline. The
     * {@code @ConditionalOnMissingBean} is not defensive programming: it is what lets a test replace
     * the repository without this configuration silently winning.
     */
    @Bean
    @ConditionalOnMissingBean
    RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    /**
     * Issued authorizations in {@code oauth2_authorization}. Persisting them is what makes a refresh
     * token survive a restart of this service — the signing key does not (see {@link JwkSourceConfig}),
     * and pretending otherwise by keeping authorizations in memory would only hide it.
     */
    @Bean
    @ConditionalOnMissingBean
    OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbcTemplate, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, clients);
    }

    /** Declared so the two repositories above get a {@link JdbcTemplate} without the JPA module. */
    @Bean
    @ConditionalOnMissingBean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
