package dev.sandoretti.ukun.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * How a test of this suite gets an access token — the two ways there are, in one place.
 *
 * <p>They are together because they are the same decision seen from both sides. {@link #of} asks the
 * running server for a real one, which is what a test asserting normal behaviour should do; the other
 * signs one by hand, which is only justified for the assertions the server refuses to make possible.
 * Keeping them apart would hide that the second is the exception.
 */
final class AccessTokens {

    /** The only client allowed the {@code client_credentials} grant (RegisteredClientSeeder). */
    private static final String API_CLIENT = "ukun-api";

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {};

    private AccessTokens() {}

    /**
     * A real token for {@code ukun-api}, obtained the way that service obtains it: {@code
     * client_credentials} against {@code /oauth2/token}, with the {@code accounts:write} scope.
     */
    static String of(RestTestClient http, String clientSecret) {
        return (String) responseOf(http, clientSecret).get("access_token");
    }

    /**
     * The whole token response, for the tests that assert on it rather than on what it opens —
     * {@code token_type}, {@code expires_in} and the JWT to be parsed and inspected.
     */
    static Map<String, Object> responseOf(RestTestClient http, String clientSecret) {
        return http.post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth(API_CLIENT, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials&scope=accounts:write")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JSON)
                .returnResult()
                .getResponseBody();
    }

    /**
     * A token signed with the running server's own key but carrying whatever scopes the test says.
     *
     * <p>It exists for one assertion that is otherwise unreachable: that {@code /internal/accounts}
     * answers {@code 403} to a caller <em>without</em> {@code accounts:write}. Neither registered
     * client can produce such a token — {@code ukun-api} always gets that scope and
     * {@code ukun-gateway} is not allowed client credentials at all — so the only way to exercise the
     * rule is to sign one here.
     *
     * <p>Signing rather than stubbing the decoder is what makes that test worth having: the token goes
     * through the real filter chain, the real decoder and the real authority mapping, and only the
     * scope is under the test's control. A mocked {@code JwtDecoder} would prove that a mock returns
     * what it was told to.
     */
    static String signedWithScopes(JWKSource<SecurityContext> jwkSource, String issuer, String... scopes) {
        try {
            RSAKey key = (RSAKey) jwkSource
                    .get(new JWKSelector(new JWKMatcher.Builder().build()), null)
                    .getFirst();

            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(API_CLIENT)
                    .issuer(issuer)
                    .audience(API_CLIENT)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(300)))
                    .claim("scope", List.of(scopes))
                    .build();

            SignedJWT token = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(key.getKeyID())
                            .build(),
                    claims);
            token.sign(new RSASSASigner(key.toPrivateKey()));

            return token.serialize();

        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign a test token with the server's own key", e);
        }
    }
}
