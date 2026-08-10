package dev.sandoretti.ukun.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.gateway.support.UpstreamStub;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

/**
 * The development shape of the same edge: under {@code tilt up} the SPA is Vite's dev server, so the
 * gateway proxies to it instead of serving a build ({@code docs/06-entorno-local-tilt.md}). This is
 * the arrangement anyone working on the project uses every day, which is reason enough for it not to
 * be the one arrangement without a test.
 *
 * <p>What matters is that switching the profile does not change any of the decisions around it: the
 * backend routes still win, and {@code /internal/**} is still a 404 rather than whatever Vite would
 * have answered for an unknown path. Both come from {@code ReservedPaths} being the single list that
 * the static fallback and the development route share.
 */
@ActiveProfiles("dev")
class DevSpaRoutingIT extends GatewayIntegrationTest {

    @Test
    @DisplayName("proxies a client-side route to the dev server")
    void proxiesAClientSideRouteToTheDevServer() {
        http.get().uri("/orders/42").accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody().json(UpstreamStub.WEB.echoOf("/orders/42"));
    }

    @Test
    @DisplayName("still routes the backend prefixes to their own service")
    void stillRoutesTheBackendPrefixes() {
        http.get().uri("/api/products")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json(UpstreamStub.API.echoOf("/api/products"));

        assertThat(UpstreamStub.WEB.requestedPaths()).isEmpty();
    }

    @Test
    @DisplayName("still hides the internal endpoints of ukun-auth")
    void stillHidesTheInternalEndpoints() {
        http.get().uri("/internal/accounts")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(UpstreamStub.WEB.requestedPaths()).isEmpty();
        assertThat(UpstreamStub.AUTH.requestedPaths()).isEmpty();
    }

    /**
     * The relaxations Vite needs through a proxy: its injected React Refresh preamble is an inline
     * script and its HMR channel is a websocket. Asserting them here is what keeps someone from
     * "fixing" the production policy when what actually needed relaxing was this one.
     */
    @Test
    @DisplayName("relaxes the content security policy for the dev server")
    void relaxesTheContentSecurityPolicy() {
        String policy = http.get().uri("/api/products")
                .exchange()
                .returnResult(Void.class)
                .getResponseHeaders()
                .getFirst("Content-Security-Policy");

        assertThat(policy)
                .contains("script-src 'self' 'unsafe-inline'")
                .contains("connect-src 'self' ws:");
    }
}
