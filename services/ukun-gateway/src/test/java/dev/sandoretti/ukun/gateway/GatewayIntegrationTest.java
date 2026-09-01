package dev.sandoretti.ukun.gateway;

import dev.sandoretti.ukun.gateway.support.GatewayDatabase;
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
 * <p>Two HTTP boundaries, which the stubs stand in for, and — since v1.0 — a real Postgres. This
 * service had no database until ADR-0024 put the BFF's session in one; now the context does not start
 * without it, so {@link GatewayDatabase} is as much a part of this fixture as the stubs are.
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
        // Since ADR-0024 the session lives in a table, so no context of this service starts without a
        // database. Registered here and not in a shared method: this one is part of the context cache
        // key (skill `testing`).
        GatewayDatabase.bind(registry);
    }

    /**
     * The pair the SPA is written against: the cookie it reads and the header it echoes it in. Named
     * here rather than in the one test that is about CSRF, because every mutation in every subclass
     * now depends on them and two spellings of the same string is exactly how that stops being true.
     */
    protected static final String CSRF_COOKIE = "XSRF-TOKEN";

    protected static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @LocalServerPort
    private int port;

    protected WebTestClient http;

    /**
     * The same client with a valid CSRF token on every request, for the tests whose subject is not
     * CSRF. Since issue #28 the token is checked before routing, before the size limit and before
     * authorisation, so a mutation without one never reaches the thing under test — a test that wants
     * to assert a 413 or a 404 has to be a request that is otherwise entirely valid.
     */
    protected WebTestClient csrfProtected;

    @BeforeEach
    void bindToTheRunningGateway() {
        http = WebTestClient.bindToServer().baseUrl("http://localhost:%d".formatted(port)).build();

        String token = issuedCsrfToken();
        csrfProtected = http.mutate()
                .defaultCookie(CSRF_COOKIE, token)
                .defaultHeader(CSRF_HEADER, token)
                .build();
    }

    /**
     * Fetched from {@code /actuator/health} on purpose. Any response carries the cookie, but this one
     * is served by this service, so acquiring the token forwards nothing to a stub and leaves the
     * recorded requests a test then asserts on exactly as that test left them.
     */
    private String issuedCsrfToken() {
        return http.get()
                .uri("/actuator/health")
                .exchange()
                .returnResult(Void.class)
                .getResponseCookies()
                .getFirst(CSRF_COOKIE)
                .getValue();
    }

    @AfterEach
    void forgetUpstreamRequests() {
        UpstreamStub.forgetRequests();
    }
}
