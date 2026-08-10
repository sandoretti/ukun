package dev.sandoretti.ukun.gateway;

import dev.sandoretti.ukun.gateway.support.UpstreamStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The shared setup of the routing tests: the gateway on an ephemeral port, its two backends pointed
 * at {@link UpstreamStub}, and a {@code WebTestClient} that talks to it the way a browser would.
 *
 * <p>The Spring annotations live on this class rather than on each test for the reason
 * {@code ukun-api}'s fixtures give: the {@code @DynamicPropertySource} method takes part in the
 * context cache key, so declaring it once is what lets five test classes share one application
 * context instead of starting five.
 *
 * <p>There is no Testcontainers here and no database — this service has neither. What it has is two
 * HTTP boundaries, and those are what the stubs stand in for.
 *
 * <p>The client is built by hand against the ephemeral port instead of being injected: Boot 4 no
 * longer contributes a {@code WebTestClient} for a running server ({@code RestTestClient} is its
 * answer for that, and it is what {@code ukun-api} uses). {@code WebTestClient} is still the right
 * client <em>here</em>, because {@code RouteAuthorizationIT} needs the exchange mutators of
 * {@code spring-security-test}, which only exist for this one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class GatewayIntegrationTest {

    @DynamicPropertySource
    static void upstreams(DynamicPropertyRegistry registry) {
        UpstreamStub.bind(registry);
    }

    @LocalServerPort
    private int port;

    protected WebTestClient http;

    @BeforeEach
    void bindToTheRunningGateway() {
        http = WebTestClient.bindToServer().baseUrl("http://localhost:%d".formatted(port)).build();
    }

    @AfterEach
    void forgetUpstreamRequests() {
        UpstreamStub.forgetRequests();
    }
}
