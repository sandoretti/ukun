package dev.sandoretti.ukun.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.gateway.support.UpstreamStub;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * The routing table end to end: every prefix the contract declares reaches the service that owns it,
 * and the one prefix that must not be reachable from outside is not.
 *
 * <p>This is what replaces {@code nginx/nginx.conf} as the single entry point. Its two
 * {@code location} blocks are not ported — they pointed at the legacy monoliths, which are not in
 * service (ADR-0007) — so what is verified here is the role, not the old rules.
 */
class GatewayRoutingIT extends GatewayIntegrationTest {

    @Test
    @DisplayName("routes the public catalogue to ukun-api")
    void routesTheCatalogueToTheApi() {
        http.get().uri("/api/products")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json(UpstreamStub.API.echoOf("/api/products"));
    }

    /**
     * The gateway owns the <em>route</em>; the adapter that serves the bytes lives in the
     * {@code storage} module of {@code ukun-api}, because that is what becomes an S3 client in v1.1
     * ({@code docs/08-identidad-y-seguridad.md} §9). No file directory is configured in this service,
     * and this test is what says so.
     */
    @Test
    @DisplayName("routes product images to ukun-api instead of serving them")
    void routesProductImagesToTheApi() {
        http.get().uri("/media/products/leather-sofa.jpg")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json(UpstreamStub.API.echoOf("/media/products/leather-sofa.jpg"));
    }

    @Test
    @DisplayName("routes what the browser needs of the authorization server to ukun-auth")
    void routesTheAuthorizationServerToAuth() {
        http.get().uri("/oauth2/jwks")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json(UpstreamStub.AUTH.echoOf("/oauth2/jwks"));

        http.get().uri("/.well-known/openid-configuration")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json(UpstreamStub.AUTH.echoOf("/.well-known/openid-configuration"));
    }

    /**
     * The signup saga's endpoints, which only {@code ukun-api} may call and only from inside the
     * container network ({@code docs/08} §3). Both halves are the same fact and belong in one test:
     * the client is told nothing exists, and nothing was forwarded to {@code ukun-auth} — a 404
     * produced by that service would mean the path <em>is</em> reachable through the edge.
     *
     * <p>With a CSRF token, so that the 404 is reached on its own merits. Without one the answer is a
     * 403 from a filter that never gets as far as asking whether the route exists — true, but it
     * would prove nothing about routing, which is what this class is for.
     */
    @Test
    @DisplayName("does not expose the internal endpoints of ukun-auth")
    void doesNotExposeTheInternalEndpoints() {
        csrfProtected.post().uri("/internal/accounts")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(UpstreamStub.AUTH.requestedPaths()).isEmpty();
    }
}
