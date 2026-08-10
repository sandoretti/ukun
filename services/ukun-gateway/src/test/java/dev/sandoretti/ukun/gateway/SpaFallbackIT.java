package dev.sandoretti.ukun.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.gateway.support.UpstreamStub;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * The history fallback: an unknown path returns {@code index.html} because the router lives in the
 * browser — and never returns it for a backend prefix, which is the half that would break silently.
 *
 * <p>The bundle here is one file under {@code src/test/resources/spa/}, bound through
 * {@code ukun.gateway.web-root}. What matters is not its content but which requests reach it.
 */
class SpaFallbackIT extends GatewayIntegrationTest {

    /**
     * The assets of the build, served from {@code web-root} rather than from {@code classpath:/static/}
     * — which is also why this mapping has to sit in front of Boot's static resource handler.
     */
    @Test
    @DisplayName("serves a file of the build as itself")
    void servesAFileOfTheBuild() {
        http.get().uri("/index.html").accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(shell -> assertThat(shell).contains("<div id=\"root\"></div>"));
    }

    @Test
    @DisplayName("answers a client-side route with the SPA shell")
    void answersAClientSideRouteWithTheShell() {
        http.get().uri("/orders/42").accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class)
                .value(shell -> assertThat(shell).contains("<div id=\"root\"></div>"));
    }

    @Test
    @DisplayName("does not answer a backend prefix with the SPA shell")
    void doesNotAnswerABackendPrefixWithTheShell() {
        http.get().uri("/api/products").accept(MediaType.TEXT_HTML)
                .exchange()
                .expectStatus().isOk()
                .expectBody().json(UpstreamStub.API.echoOf("/api/products"));
    }

    /** Served by this service, not routed and not swallowed by the fallback. */
    @Test
    @DisplayName("keeps its own actuator endpoints")
    void keepsItsOwnActuatorEndpoints() {
        http.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }
}
