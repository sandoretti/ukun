package dev.sandoretti.ukun.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.gateway.support.UpstreamStub;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Defect #17 of {@code docs/03-analisis-y-patrones.md} §1, closed at the edge: the legacy declared
 * 10 MB in Spring and nothing in nginx, so the effective limit was neither. Here the gateway cuts at
 * 5 MB, the same value {@code ukun-api} configures, and answers the {@code ProblemDetail} that
 * service would have answered — {@code docs/12-error-contract.md} calls {@code PAYLOAD_TOO_LARGE}
 * the one code of the registry with two possible emitters, precisely because of this.
 *
 * <p>Nothing needs to exist behind the gateway for the limit to hold — the upload endpoint it was
 * written for arrives in v0.7 — and the assertion that the upstream received nothing is what proves
 * the request was cut here rather than forwarded.
 */
class PayloadLimitIT extends GatewayIntegrationTest {

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    /**
     * A public route, deliberately: the size filter runs <em>after</em> the security chain, so on a
     * protected path the 401 comes first and there would be no 413 to look at. See
     * {@link #decidesAuthorisationBeforeSize}.
     */
    private static final String PUBLIC_POST = "/api/signup";

    @Test
    @DisplayName("answers 413 with the ProblemDetail of the contract when the body is too large")
    void answers413WhenTheBodyIsTooLarge() {
        http.post()
                .uri(PUBLIC_POST)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(new byte[(int) MAX_BYTES + 1])
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONTENT_TOO_LARGE)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://ukun.dev/errors/payload-too-large")
                .jsonPath("$.title").isEqualTo("Payload too large")
                .jsonPath("$.status").isEqualTo(413)
                .jsonPath("$.code").isEqualTo("PAYLOAD_TOO_LARGE")
                .jsonPath("$.maxBytes").isEqualTo(MAX_BYTES)
                .jsonPath("$.traceId").isNotEmpty();

        assertThat(UpstreamStub.API.requestedPaths()).isEmpty();
    }

    @Test
    @DisplayName("forwards a body at the limit")
    void forwardsABodyAtTheLimit() {
        http.post()
                .uri(PUBLIC_POST)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(new byte[(int) MAX_BYTES])
                .exchange()
                .expectStatus().isOk();

        assertThat(UpstreamStub.API.requestedPaths()).containsExactly(PUBLIC_POST);
    }

    /**
     * The consequence of the filter's {@code @Order(0)}, fixed as behaviour rather than left as a
     * comment: an anonymous caller pushing 6 MB at an admin route is told it is not their route, not
     * how large a body this gateway accepts. The ordering exists so that the 413 itself can carry
     * the four security headers, which are written from inside the security chain.
     */
    @Test
    @DisplayName("decides authorisation before size on a protected route")
    void decidesAuthorisationBeforeSize() {
        http.post()
                .uri("/api/admin/products/42/image")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(new byte[(int) MAX_BYTES + 1])
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");

        assertThat(UpstreamStub.API.requestedPaths()).isEmpty();
    }
}
