package dev.sandoretti.ukun.auth;

import static dev.sandoretti.ukun.auth.AccountMother.PASSWORD;
import static dev.sandoretti.ukun.auth.AuthorizationCodeFlow.GATEWAY_POST_LOGOUT_URI;
import static dev.sandoretti.ukun.auth.AuthorizationCodeFlow.GATEWAY_REDIRECT_URI;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.auth.domain.Role;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The other end of signing out: the gateway destroys its own session and forwards the browser here,
 * and this is where the session that actually remembers the password ends.
 *
 * <h2>Why this class exists</h2>
 *
 * The gateway's {@code LogoutIT} asserts the {@code Location} it emits towards a <em>stub</em> of this
 * service, so it cannot see whether the endpoint accepts it. It did not: the whole of issue #106 —
 * {@code /connect/logout} answering 400, the session surviving, and «sign in» walking the previous
 * user straight back in — was invisible to every test in the repository, and visible on the first
 * click in a browser.
 *
 * <p>Hence the shape of these two tests. The first is the mechanism; the second is what a person
 * would call "logging out", and it is the one that fails if the session outlives the request.
 */
class RpInitiatedLogoutIT extends LoginIntegrationTest {

    /**
     * Its own account, not the shared {@code CUSTOMER_EMAIL}. The login limiter buckets attempts by
     * {@code IP + email} with room for five a minute (docs/08 §8), and every fixture in this suite
     * logs in from the same address: two more logins on the shared account are enough to make a
     * different class fail with «no code», which reads like a broken authorization endpoint and is
     * really an exhausted budget.
     */
    private static final String EMAIL = "signing-out@ukun.dev";

    @Test
    @DisplayName("the end session endpoint ends the session and sends the browser back to the client")
    void endSessionRedirectsToThePostLogoutUri() {
        accounts().account(EMAIL, PASSWORD, true, Role.CUSTOMER);
        String session = logIn(EMAIL, PASSWORD).sessionCookie();
        String idToken = (String) tokens(session).get("id_token");

        String location = endSession(session, idToken);

        assertThat(location).isEqualTo(GATEWAY_POST_LOGOUT_URI);
    }

    /**
     * Reading the claims back out of {@code oauth2_authorization} is what the endpoint does first, and
     * an immutable collection in any of them is enough to make it fail (issue #106): the token endpoint
     * writes the row and this is the first request that reads it.
     */
    @Test
    @DisplayName("after signing out, authorising again asks for the password instead of issuing a code")
    void signingOutMakesTheNextAuthorizationAskForCredentials() {
        accounts().account(EMAIL, PASSWORD, true, Role.CUSTOMER);
        String session = logIn(EMAIL, PASSWORD).sessionCookie();
        String idToken = (String) tokens(session).get("id_token");

        endSession(session, idToken);

        String location = new AuthorizationCodeFlow(http, GATEWAY_SECRET)
                .authorize(session, authorizationRequest());
        assertThat(location)
                .as("the session survived the logout, so the server issued a code without asking")
                .contains(FormLogin.LOGIN_PATH);
    }

    private Map<String, Object> tokens(String sessionCookie) {
        return new AuthorizationCodeFlow(http, GATEWAY_SECRET).tokensFor(sessionCookie);
    }

    /** The GET a browser makes when the gateway forwards it here, cookie included. */
    private String endSession(String sessionCookie, String idToken) {
        return http.get()
                .uri(UriComponentsBuilder.fromPath("/connect/logout")
                        .queryParam("id_token_hint", idToken)
                        .queryParam("post_logout_redirect_uri", GATEWAY_POST_LOGOUT_URI)
                        .build()
                        .encode()
                        .toUri())
                .header(HttpHeaders.COOKIE, sessionCookie)
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .returnResult()
                .getResponseHeaders()
                .getFirst(HttpHeaders.LOCATION);
    }

    private static URI authorizationRequest() {
        return UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", "ukun-gateway")
                .queryParam("scope", "openid profile")
                .queryParam("redirect_uri", GATEWAY_REDIRECT_URI)
                .queryParam("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUri();
    }
}
