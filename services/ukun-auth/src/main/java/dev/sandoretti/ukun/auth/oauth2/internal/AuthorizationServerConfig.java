package dev.sandoretti.ukun.auth.oauth2.internal;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated());

        return http.build();
    }

    /**
     * Everything that is not an Authorization Server endpoint. The health probe is public because
     * compose's {@code HEALTHCHECK} calls it without credentials; the rest is closed.
     *
     * <p>There is no authentication mechanism on this chain yet, so today it answers 401 to
     * everything else — which is correct: the only flow that works at this point is client
     * credentials, and that authenticates on the chain above. The login form arrives in the next
     * commit, together with the events and the attempt limit.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests -> requests.requestMatchers("/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .httpBasic(Customizer.withDefaults());

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
