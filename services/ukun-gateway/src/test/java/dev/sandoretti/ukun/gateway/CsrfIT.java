package dev.sandoretti.ukun.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.gateway.support.UpstreamStub;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CSRF protection at the edge, which is the half of ADR-0003 that "the token is not in JavaScript"
 * does not cover on its own: a session cookie is still sent by the browser on a request some other
 * page caused, so the mutation has to prove it came from our own code.
 *
 * <p>The legacy had none of this, and worse — it mutated on {@code GET}
 * ({@code docs/03-analisis-y-patrones.md} §1), which makes a CSRF a link. Half the fix is the REST
 * contract; this is the other half.
 *
 * <p>These run against the real port rather than the application context on purpose. The mechanism
 * under test <em>is</em> the cookie and the header — a mutator that installs a valid token would be
 * testing that Spring Security agrees with itself.
 */
class CsrfIT extends GatewayIntegrationTest {

    private static final String COOKIE = "XSRF-TOKEN";
    private static final String HEADER = "X-XSRF-TOKEN";

    /** {@code /api/products} is public at this layer (ADR-0020), so CSRF is the only thing gating it. */
    private static final String MUTATION = "/api/products";

    /**
     * The cookie has to be readable by JavaScript — that is what the SPA echoes back — and it has to
     * come out at all, which is not free in WebFlux: {@code CsrfWebFilter} only stores a lazy
     * {@code Mono<CsrfToken>}, and without {@code CsrfCookieWebFilter} subscribing to it nothing is
     * ever written. The failure that omission produces is this test passing its first line and every
     * mutation answering 403 forever.
     */
    @Test
    @DisplayName("hands the browser a readable XSRF-TOKEN cookie")
    void handsTheBrowserAReadableToken() {
        http.get()
                .uri("/api/products")
                .exchange()
                .expectStatus()
                .isOk()
                .expectCookie()
                .exists(COOKIE)
                .expectCookie()
                .httpOnly(COOKIE, false)
                .expectCookie()
                .sameSite(COOKIE, "Lax");
    }

    /**
     * And the shape matters as much as the status: a frontend switching on {@code code} has to see
     * the same {@code FORBIDDEN} the resource server would have produced. Nothing extra wires this —
     * {@code CsrfException} is an {@code AccessDeniedException}, so it lands on the handler that was
     * already there, which is why no new code was invented for a closed registry.
     */
    @Test
    @DisplayName("refuses a mutation with no token, as a ProblemDetail")
    void refusesAMutationWithNoToken() {
        http.post()
                .uri(MUTATION)
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("FORBIDDEN")
                .jsonPath("$.traceId")
                .isNotEmpty();

        assertThat(UpstreamStub.API.requestedPaths()).isEmpty();
    }

    /**
     * The positive case, and the one that catches the subtler bug: with the default
     * {@code XorServerCsrfTokenRequestAttributeHandler} the cookie holds the raw token while the
     * server expects a BREACH-encoded one, so echoing the cookie verbatim — which is what every SPA
     * does — would fail. {@code csrfProtected} does exactly that, cookie value straight into the
     * header, so this passing is the assertion that the two are the same string.
     */
    @Test
    @DisplayName("lets a mutation through when the cookie is echoed in the header")
    void letsAMutationThroughWithTheToken() {
        csrfProtected.post().uri(MUTATION).exchange().expectStatus().isOk();

        assertThat(UpstreamStub.API.requestedPaths()).containsExactly(MUTATION);
    }
}
