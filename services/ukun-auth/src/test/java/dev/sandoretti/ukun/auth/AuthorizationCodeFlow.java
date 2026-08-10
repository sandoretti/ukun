package dev.sandoretti.ukun.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The authorization code + PKCE dance, run the way the gateway runs it: authorize with the browser's
 * session, then exchange the code for tokens as the client.
 *
 * <p>It is a collaborator next to {@link FormLogin}, and for the same reason: two fixtures need it.
 * {@code TokenClaimsIT} reads the claims of the access token that comes out, and
 * {@code RpInitiatedLogoutIT} needs the <em>id token</em> from the same response to hand it back as
 * {@code id_token_hint}. Neither can inherit it from the other.
 */
final class AuthorizationCodeFlow {

    /** Must match what the seeder registers, or the authorization request is rejected. */
    static final String GATEWAY_REDIRECT_URI = "http://localhost:8080/login/oauth2/code/ukun";

    /** Registered next to it, and what the end session endpoint compares against as a literal string. */
    static final String GATEWAY_POST_LOGOUT_URI = "http://localhost:8080/";

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {};

    private final RestTestClient http;
    private final String clientSecret;

    AuthorizationCodeFlow(RestTestClient http, String clientSecret) {
        this.http = http;
        this.clientSecret = clientSecret;
    }

    /**
     * @return the token endpoint's response — {@code access_token}, {@code id_token} and the rest
     */
    Map<String, Object> tokensFor(String sessionCookie) {
        String verifier = randomCodeVerifier();
        String location = authorize(sessionCookie, UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", "ukun-gateway")
                .queryParam("scope", "openid profile ukun.read ukun.write")
                .queryParam("redirect_uri", GATEWAY_REDIRECT_URI)
                .queryParam("code_challenge", challengeOf(verifier))
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUri());

        String code = queryParameter(location, "code");
        assertThat(code).as("the authorization endpoint returned no code: %s", location).isNotNull();

        return http.post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth("ukun-gateway", clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=authorization_code&code=%s&redirect_uri=%s&code_verifier=%s"
                        .formatted(code, urlEncode(GATEWAY_REDIRECT_URI), verifier))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JSON_OBJECT)
                .returnResult()
                .getResponseBody();
    }

    /**
     * Returns the {@code Location} of the redirect, which carries either the code or the error.
     *
     * <p>The URI goes in already built, through the {@code uri(URI)} overload rather than
     * {@code uri(String, Object...)}. The latter treats its argument as a URI <em>template</em> and
     * encodes it again, so the {@code %20} separating the scopes arrives as {@code %2520} and the
     * server answers {@code invalid_scope} — a failure that reads like a misconfigured client and is
     * really an over-encoded query string.
     */
    String authorize(String sessionCookie, URI uri) {
        return http.get()
                .uri(uri)
                .header(HttpHeaders.COOKIE, sessionCookie)
                .exchange()
                .expectStatus()
                .is3xxRedirection()
                .returnResult()
                .getResponseHeaders()
                .getFirst(HttpHeaders.LOCATION);
    }

    static String queryParameter(String location, String name) {
        return UriComponentsBuilder.fromUri(URI.create(location))
                .build()
                .getQueryParams()
                .getFirst(name);
    }

    private static String randomCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String challengeOf(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JVM has no SHA-256", e);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
