package dev.sandoretti.ukun.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.gateway.support.UpstreamStub;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The start of the authorization code flow: what {@code /oauth2/authorization/ukun} actually sends
 * the browser to.
 *
 * <p>Three things are being fixed here, and each of them is a way this slice could silently be wrong
 * while every other test stayed green.
 */
class OAuth2LoginIT extends GatewayIntegrationTest {

    private static final String START_LOGIN = "/oauth2/authorization/ukun";

    /**
     * The local endpoint wins over the {@code /oauth2/**} route to {@code ukun-auth}, and it wins
     * because every {@code WebFilter} runs before the first {@code HandlerMapping} — not because of
     * an order somebody could change. A {@code securityMatcher} narrowing the chain would break it,
     * and the symptom would be this redirect being proxied instead of produced, which is exactly what
     * the second half of this test rules out.
     */
    @Test
    @DisplayName("starts the flow locally instead of proxying it to ukun-auth")
    void startsTheFlowLocally() {
        String location = redirectOfLogin();

        assertThat(location).startsWith(UpstreamStub.BROWSER_FACING_ISSUER + "/oauth2/authorize");
        assertThat(UpstreamStub.AUTH.requestedPaths()).isEmpty();
    }

    /**
     * The browser-facing half of the configuration split. The gateway reaches {@code ukun-auth} by
     * container name, but the URL it puts in front of a person has to be one their machine can
     * resolve — here the stub is <em>not</em> where the redirect points, which is the whole assertion.
     */
    @Test
    @DisplayName("sends the browser to the issuer's own origin, not to the container name")
    void sendsTheBrowserToTheIssuersOrigin() {
        MultiValueMap<String, String> parameters = parametersOf(redirectOfLogin());

        assertThat(parameters.getFirst("client_id")).isEqualTo("ukun-gateway");
        assertThat(parameters.getFirst("response_type")).isEqualTo("code");
        assertThat(parameters.getFirst("scope")).contains("openid", "ukun.read", "ukun.write");
        assertThat(parameters.getFirst("redirect_uri")).endsWith("/login/oauth2/code/ukun");
    }

    /**
     * PKCE is configured nowhere in this service — {@code ukun-auth} registered the client with
     * {@code requireProofKey(true)} and Spring adds the challenge on its own. Which is precisely why
     * it deserves a test: nothing in this repository would break visibly if it stopped happening, and
     * the authorization server would simply start rejecting every login.
     */
    @Test
    @DisplayName("sends a PKCE challenge without anything here asking for one")
    void sendsAPkceChallenge() {
        MultiValueMap<String, String> parameters = parametersOf(redirectOfLogin());

        assertThat(parameters.getFirst("code_challenge")).isNotBlank();
        assertThat(parameters.getFirst("code_challenge_method")).isEqualTo("S256");
    }

    private String redirectOfLogin() {
        return http.get()
                .uri(START_LOGIN)
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .returnResult(Void.class)
                .getResponseHeaders()
                .getFirst("Location");
    }

    private static MultiValueMap<String, String> parametersOf(String location) {
        return UriComponentsBuilder.fromUri(URI.create(location)).build().getQueryParams();
    }
}
