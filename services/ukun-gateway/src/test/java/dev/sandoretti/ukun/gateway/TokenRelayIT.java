package dev.sandoretti.ukun.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOAuth2Login;

import dev.sandoretti.ukun.gateway.support.UpstreamStub;
import dev.sandoretti.ukun.gateway.support.UpstreamStub.Received;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The relay: a session on the way in, a {@code Bearer} token on the way out, and the browser never
 * holding either of them (ADR-0003).
 *
 * <p>Both halves are asserted from the <em>receiving</em> end, through what {@link UpstreamStub}
 * recorded, rather than from the response. That is the only place the question can actually be
 * answered: whether the gateway attached an {@code Authorization} header is invisible to the client
 * that made the request, which is the entire point of the pattern.
 */
class TokenRelayIT extends FilterChainIntegrationTest {

    @Test
    @DisplayName("attaches the access token to what it proxies to ukun-api")
    void attachesTheAccessTokenTowardsTheApi() {
        http.mutateWith(mockOAuth2Login())
                .get()
                .uri("/api/me/profile")
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(UpstreamStub.API.received())
                .singleElement()
                .extracting(received -> received.header("Authorization"))
                .asString()
                .startsWith("Bearer ");
    }

    /**
     * The other half, and the one worth a test of its own: {@code TokenRelay} is declared on the
     * {@code api} route only. Sending a bearer token to the authorization server is not a harmless
     * redundancy — it hands a credential minted for {@code ukun-api} to a party that has no business
     * seeing it, on requests ({@code /oauth2/jwks}, the discovery document) that are public by design.
     */
    @Test
    @DisplayName("does not attach it to anything going to ukun-auth")
    void doesNotAttachItTowardsAuth() {
        http.mutateWith(mockOAuth2Login())
                .get()
                .uri("/oauth2/jwks")
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(UpstreamStub.AUTH.received())
                .singleElement()
                .extracting(Received::headers, MAP)
                .doesNotContainKey("authorization");
    }

    /**
     * No session, no header, and — this is the part that would be easy to get wrong — no failure
     * either. The public catalogue has to keep working for an anonymous browser, so the filter can
     * only add the token when there is one to add.
     */
    @Test
    @DisplayName("leaves an anonymous request alone")
    void leavesAnAnonymousRequestAlone() {
        http.get().uri("/api/products").exchange().expectStatus().isOk();

        assertThat(UpstreamStub.API.received())
                .singleElement()
                .extracting(Received::headers, MAP)
                .doesNotContainKey("authorization");
    }
}
