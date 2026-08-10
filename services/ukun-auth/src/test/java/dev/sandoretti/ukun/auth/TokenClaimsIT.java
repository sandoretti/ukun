package dev.sandoretti.ukun.auth;

import static dev.sandoretti.ukun.auth.AccountMother.CUSTOMER_EMAIL;
import static dev.sandoretti.ukun.auth.AccountMother.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The access token of {@code docs/08-identidad-y-seguridad.md} §4, obtained the way the gateway will
 * obtain it in v0.5: authorization code with PKCE, on behalf of somebody who logged in.
 *
 * <p>This is the half {@code AuthorizationServerIT} cannot reach — client credentials produces a
 * token with no person behind it — and it is the half issue #24 depends on: every claim asserted
 * here is one {@code ukun-api} reads.
 */
class TokenClaimsIT extends LoginIntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {};

    @Test
    @DisplayName("the token identifies the account by UUID, not by the email somebody typed")
    void subjectIsTheAccountIdAndNotTheEmail() throws ParseException {
        UUID accountId = accounts().customer();

        SignedJWT token = userAccessToken(CUSTOMER_EMAIL, PASSWORD);

        assertThat(token.getJWTClaimsSet().getSubject()).isEqualTo(accountId.toString());
        assertThat(token.getJWTClaimsSet().getStringClaim("email")).isEqualTo(CUSTOMER_EMAIL);
    }

    /**
     * Without this the audience is the {@code client_id} — the gateway — and {@code ukun-api}, which
     * validates {@code aud=ukun-api}, would reject every token the BFF relayed to it.
     */
    @Test
    @DisplayName("the audience is the resource server, not the client that asked for the token")
    void audienceIsTheResourceServer() throws ParseException {
        accounts().customer();

        SignedJWT token = userAccessToken(CUSTOMER_EMAIL, PASSWORD);

        assertThat(token.getJWTClaimsSet().getAudience()).containsExactly("ukun-api");
    }

    /** ADR-0006: one business role, plus the admin sub-flag for an employee. */
    @Test
    @DisplayName("the roles claim carries the business roles of the account")
    void rolesTravelInTheToken() throws ParseException {
        accounts().admin("boss@ukun.dev");

        SignedJWT token = userAccessToken("boss@ukun.dev", PASSWORD);

        assertThat(token.getJWTClaimsSet().getStringListClaim("roles"))
                .containsExactlyInAnyOrder("EMPLOYEE", "ADMIN");
    }

    /**
     * The claim {@code docs/08} §4 argues at length for leaving out. Checked on a user token because
     * that is the only one it could plausibly have been added to.
     */
    @Test
    @DisplayName("a user token carries no storeId either")
    void noStoreIdInAUserToken() throws ParseException {
        accounts().admin("boss@ukun.dev");

        SignedJWT token = userAccessToken("boss@ukun.dev", PASSWORD);

        assertThat(token.getJWTClaimsSet().getClaims()).doesNotContainKey("storeId");
    }

    /**
     * PKCE is required for the gateway client, so an authorization request without a challenge has to
     * be refused. Otherwise the protection is a setting nobody would notice losing.
     */
    @Test
    @DisplayName("the gateway cannot start an authorization request without PKCE")
    void pkceIsNotOptional() {
        accounts().customer();
        String session = logIn(CUSTOMER_EMAIL, PASSWORD).sessionCookie();

        String location = authorize(session, UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", "ukun-gateway")
                .queryParam("scope", "openid profile")
                .queryParam("redirect_uri", GATEWAY_REDIRECT_URI)
                .build()
                .encode()
                .toUri());

        assertThat(location).contains("error=invalid_request");
    }

    /** The whole authorization code + PKCE dance, as the gateway will run it. */
    private SignedJWT userAccessToken(String email, String password) throws ParseException {
        String session = logIn(email, password).sessionCookie();

        String verifier = randomCodeVerifier();
        String location = authorize(session, UriComponentsBuilder.fromPath("/oauth2/authorize")
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

        Map<String, Object> tokenResponse = http.post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth("ukun-gateway", GATEWAY_SECRET))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=authorization_code&code=%s&redirect_uri=%s&code_verifier=%s"
                        .formatted(code, urlEncode(GATEWAY_REDIRECT_URI), verifier))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JSON_OBJECT)
                .returnResult()
                .getResponseBody();

        return SignedJWT.parse((String) tokenResponse.get("access_token"));
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
    private String authorize(String sessionCookie, URI uri) {
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

    private static String queryParameter(String location, String name) {
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
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
