package dev.sandoretti.ukun.gateway;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOAuth2Client;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOAuth2Login;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import dev.sandoretti.ukun.gateway.support.AccessTokens;
import dev.sandoretti.ukun.gateway.support.UpstreamStub;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The shared setup of the tests that need to <em>be</em> somebody: the whole filter chain, with the
 * transport replaced so that the mutators of {@code spring-security-test} can reach the exchange.
 *
 * <h2>Why this is a second base class and not a flag on the first</h2>
 *
 * {@link GatewayIntegrationTest} binds to a real port, which is what a test about cookies, headers or
 * status codes needs. {@code mockUser()} and {@code mockOAuth2Login()} cannot work there at all: they
 * authenticate by mutating the {@code ServerWebExchange}, and a client talking to a socket has no
 * exchange to mutate. Binding to the application context keeps every {@code WebFilter}, the
 * dispatcher and the gateway's own handler mapping, and only replaces the wire — the hop to
 * {@link UpstreamStub} is still real HTTP.
 *
 * <p>The two therefore also differ in {@code webEnvironment}, so they are two Spring contexts no
 * matter how they are written. That is the floor, and this class is what keeps it at two: the
 * {@code @DynamicPropertySource} method takes part in the context cache key, so declaring it once
 * here is what lets three test classes share one context instead of starting three.
 */
@SpringBootTest
abstract class FilterChainIntegrationTest {

    @DynamicPropertySource
    static void upstreams(DynamicPropertyRegistry registry) {
        UpstreamStub.bind(registry);
    }

    @Autowired
    private ApplicationContext context;

    protected WebTestClient http;

    @BeforeEach
    void bindToTheFilterChain() {
        http = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).build();
    }

    /**
     * A signed-in browser session holding an access token with these roles — the shape every test of
     * this service that needs to be somebody wants, and the one the gateway now decides on
     * ({@code RelayedTokenAuthorizationManager}).
     *
     * <p>Two things about it are load-bearing. The client mutator goes <strong>first</strong>:
     * {@code mockOAuth2Login} delegates to it internally with an access token of its own, the literal
     * string {@code "access-token"}, and applying it last leaves that in the session, where it does not
     * parse as a JWT and every protected request answers 401. And nothing is said about the principal's
     * authorities on purpose — leaving them at the mutator's default is what makes these tests show
     * that the token is what decides, which is the whole point of issue #145.
     *
     * <p>{@code AdminAuthorizationIT} does not use this and builds its own sessions instead: it is the
     * one class whose subject is the two halves <em>disagreeing</em>.
     */
    protected WebTestClient signedInWith(List<String> roles) {
        return http.mutateWith(mockOAuth2Client("test").accessToken(AccessTokens.withRoles(roles)))
                .mutateWith(mockOAuth2Login());
    }

    @AfterEach
    void forgetUpstreamRequests() {
        UpstreamStub.forgetRequests();
    }
}
