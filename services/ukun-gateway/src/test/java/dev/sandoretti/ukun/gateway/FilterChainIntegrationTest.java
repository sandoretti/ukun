package dev.sandoretti.ukun.gateway;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import dev.sandoretti.ukun.gateway.support.UpstreamStub;
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

    @AfterEach
    void forgetUpstreamRequests() {
        UpstreamStub.forgetRequests();
    }
}
