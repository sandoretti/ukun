package dev.sandoretti.ukun.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import dev.sandoretti.ukun.api.AbstractIntegrationTest;
import dev.sandoretti.ukun.api.JwksTestServer;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Whether a token is accepted, decided by the running application against a real JWKS over HTTP. The
 * other half of issue #24 — which routes are protected at all is
 * {@code SecurityRoutingTest}'s subject, in a slice with no container.
 *
 * <p>This is the test the configuration of {@code application.yaml} answers to. Three of its
 * assertions would each be a production outage of a different shape: a token from another issuer
 * accepted, a token minted for another service accepted, or an expired one still working. Spring
 * validates none of the first two by default — {@code jwk-set-uri} alone checks the signature and
 * nothing else — so they are checks this repo turned on deliberately and has to keep on.
 *
 * <p>The issuer here is not the container's: what matters is that the value the token carries and the
 * value the service was configured with are compared literally, which is exactly the failure mode of
 * pointing {@code issuer-uri} at a name only one side can resolve.
 */
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.liquibase.contexts=prod")
@Import(ProbeSecuredEndpoints.class)
class ResourceServerIT extends AbstractIntegrationTest {

    private static final JwksTestServer AUTH = JwksTestServer.start();

    private static final UUID ACCOUNT_ID = UUID.fromString("8f2c1e40-0000-4000-8000-000000000001");

    @DynamicPropertySource
    static void resourceServer(DynamicPropertyRegistry registry) {
        useDatabase(registry, SCHEMA_ONLY_DATABASE);

        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", AUTH::jwkSetUri);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> JwksTestServer.ISSUER);
    }

    @AfterAll
    static void stopAuth() throws Exception {
        AUTH.close();
    }

    @Autowired
    private RestTestClient restTestClient;

    @Test
    @DisplayName("a token issued for this service opens a protected route")
    void shouldAcceptAValidToken() {
        get(ProbeSecuredEndpoints.AUTHENTICATED, AUTH.validToken(ACCOUNT_ID, "customer@ukun.dev", List.of("CUSTOMER")))
                .expectStatus()
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * The claim travels bare and {@code hasRole()} expects a prefix, so this is what proves the two
     * ends meet. It fails if {@code authorities-claim-name} or {@code authority-prefix} is dropped from
     * the configuration — a change that otherwise shows up as a 403 nobody can explain.
     */
    @Test
    @DisplayName("the roles claim becomes the authority @PreAuthorize asks for")
    void shouldMapTheRolesClaimToAuthorities() {
        assertSoftly(softly -> {
            softly.assertThat(statusOf(
                            ProbeSecuredEndpoints.ADMIN_ONLY,
                            AUTH.validToken(ACCOUNT_ID, "admin@ukun.dev", List.of("EMPLOYEE", "ADMIN"))))
                    .isEqualTo(HttpStatus.OK);

            softly.assertThat(statusOf(
                            ProbeSecuredEndpoints.ADMIN_ONLY,
                            AUTH.validToken(ACCOUNT_ID, "customer@ukun.dev", List.of("CUSTOMER"))))
                    .isEqualTo(HttpStatus.FORBIDDEN);
        });
    }

    @Test
    @DisplayName("a token from another issuer is rejected")
    void shouldRejectAForeignIssuer() {
        String foreign = AUTH.token(claims -> claims.issuer("https://evil.example.com")
                .audience(JwksTestServer.AUDIENCE)
                .subject(ACCOUNT_ID.toString()));

        assertThat(statusOf(ProbeSecuredEndpoints.AUTHENTICATED, foreign)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * The one Spring does not check unless told to. Without {@code audiences}, a token ukun-auth minted
     * for a different resource server — or for the gateway itself — would be accepted here.
     */
    @Test
    @DisplayName("a token minted for somebody else is rejected")
    void shouldRejectAForeignAudience() {
        String elsewhere = AUTH.token(claims -> claims.issuer(JwksTestServer.ISSUER)
                .audience("another-service")
                .subject(ACCOUNT_ID.toString()));

        assertThat(statusOf(ProbeSecuredEndpoints.AUTHENTICATED, elsewhere)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("an expired token is rejected")
    void shouldRejectAnExpiredToken() {
        String expired = AUTH.token(claims -> claims.issuer(JwksTestServer.ISSUER)
                .audience(JwksTestServer.AUDIENCE)
                .subject(ACCOUNT_ID.toString())
                .expirationTime(Date.from(Instant.now().minusSeconds(60))));

        assertThat(statusOf(ProbeSecuredEndpoints.AUTHENTICATED, expired)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("a token signed with a key the JWKS does not publish is rejected")
    void shouldRejectAForeignSignature() {
        assertThat(statusOf(ProbeSecuredEndpoints.AUTHENTICATED, AUTH.tokenSignedByAnotherKey(ACCOUNT_ID)))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * The regression net for v0.3. Turning security on protects everything by default, so what has to
     * be proven is not that the new routes are closed but that the old ones stayed open — including the
     * two nobody would think of as endpoints: the health probe the container's HEALTHCHECK polls, and
     * the generated OpenAPI document seven other tests read.
     */
    @Test
    @DisplayName("everything that was public before is still reachable without a token")
    void shouldKeepThePublicSurfaceOpen() {
        assertSoftly(softly -> {
            softly.assertThat(statusOf("/api/products", null)).isEqualTo(HttpStatus.OK);
            softly.assertThat(statusOf("/api/product-types", null)).isEqualTo(HttpStatus.OK);
            softly.assertThat(statusOf("/api/stores", null)).isEqualTo(HttpStatus.OK);
            softly.assertThat(statusOf("/actuator/health", null)).isEqualTo(HttpStatus.OK);
        });
    }

    /**
     * The other half of the pair {@code ProductCatalogIT} keeps. Turning the resource server on moved
     * the answer an <em>anonymous</em> caller gets for an unknown route from {@code 404} to
     * {@code 401} — the filter chain runs before handler mapping, and telling a stranger which paths
     * exist is a worse trade than the lost precision. What must not change is that the two protocol
     * codes of {@code docs/12-error-contract.md} §2 still reach somebody entitled to them.
     */
    @Test
    @DisplayName("the protocol codes survive behind authentication")
    void shouldStillClassifyRoutesAndMethodsForAnAuthenticatedCaller() {
        String token = AUTH.validToken(ACCOUNT_ID, "customer@ukun.dev", List.of("CUSTOMER"));

        get("/api/does-not-exist", token)
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("RESOURCE_NOT_FOUND");

        restTestClient
                .post()
                .uri("/api/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    @DisplayName("a rejected request answers with the error contract, not an empty body")
    void shouldAnswerRejectionsWithProblemDetail() {
        restTestClient
                .get()
                .uri(ProbeSecuredEndpoints.AUTHENTICATED)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.traceId")
                .exists();
    }

    private RestTestClient.ResponseSpec get(String uri, String token) {
        var request = restTestClient.get().uri(uri);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        return request.exchange();
    }

    /** {@code String} rather than {@code Void}: the probe answers {@code text/plain} and every other
     * route here answers JSON, and the string converter reads both without the test caring which. */
    private HttpStatus statusOf(String uri, String token) {
        return HttpStatus.valueOf(
                get(uri, token).returnResult(String.class).getStatus().value());
    }
}
