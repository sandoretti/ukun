package dev.sandoretti.ukun.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The criterion issue #22 states, asked of a running server: <em>«el servidor emite un token y
 * publica el JWKS»</em>. That is what unblocks issue #24 — {@code ukun-api} as a resource server
 * needs a signed token and a key set to validate it against, and nothing more from this service.
 *
 * <p>The flow exercised here is client credentials, which is the whole of what {@code ukun-api} uses
 * to call {@code /internal/accounts} (docs/08 §3). The claims of a <em>user</em> token — {@code sub}
 * as the account UUID, {@code email}, {@code roles} — need somebody to log in, so they are asserted
 * in {@code TokenClaimsIT} once the login form exists.
 */
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.liquibase.contexts=prod",
            "ukun.oauth2.gateway.secret=" + AbstractIntegrationTest.GATEWAY_SECRET,
            "ukun.oauth2.api.secret=" + AbstractIntegrationTest.API_SECRET
        })
class AuthorizationServerIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, ACCOUNTS_DATABASE);
    }

    @Autowired
    private RestTestClient http;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("the server issues a signed access token for the client credentials of ukun-api")
    void issuesASignedTokenForClientCredentials() throws ParseException {
        Map<String, Object> response = AccessTokens.responseOf(http, API_SECRET);

        assertThat(response).containsEntry("token_type", "Bearer").containsKey("expires_in");

        SignedJWT token = SignedJWT.parse((String) response.get("access_token"));

        assertThat(token.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(token.getJWTClaimsSet().getSubject()).isEqualTo("ukun-api");

        // Inside the JWT `scope` is a JSON array, even for a single scope — it is only the
        // space-delimited string of RFC 6749 in the token *response*, which is a different document.
        assertThat(token.getJWTClaimsSet().getStringListClaim("scope")).containsExactly("accounts:write");
    }

    /**
     * A service token has no person behind it, so it carries neither the email nor the roles of one.
     * Asserted rather than assumed: the customizer decides this by grant type, and getting it wrong
     * would mean {@code ukun-api}'s own service token claimed to be a customer.
     */
    @Test
    @DisplayName("a client credentials token carries no identity of a person")
    void aServiceTokenCarriesNoPersonalIdentity() throws ParseException {
        SignedJWT token = SignedJWT.parse((String) AccessTokens.responseOf(http, API_SECRET).get("access_token"));

        assertThat(token.getJWTClaimsSet().getClaims()).doesNotContainKeys("email", "roles");
    }

    /**
     * The claim {@code docs/08} §4 spends a paragraph on not being there. It is checked on every
     * token this suite can obtain, because the cost of it appearing is not a failing test somewhere:
     * it is an employee transfer that does not take effect until a token expires.
     */
    @Test
    @DisplayName("no token ever carries a storeId")
    void noTokenCarriesAStoreId() throws ParseException {
        SignedJWT token = SignedJWT.parse((String) AccessTokens.responseOf(http, API_SECRET).get("access_token"));

        assertThat(token.getJWTClaimsSet().getClaims()).doesNotContainKey("storeId");
    }

    @Test
    @DisplayName("the JWKS publishes one RSA verification key and no private material")
    void publishesTheVerificationKey() {
        Map<String, Object> jwks = http.get()
                .uri("/oauth2/jwks")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult()
                .getResponseBody();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");

        assertThat(keys).singleElement().satisfies(key -> {
            assertThat(key).containsEntry("kty", "RSA").containsKeys("n", "e", "kid");
            // "d" is the private exponent. A JWKS that leaks it hands out the signing key itself.
            assertThat(key).doesNotContainKeys("d", "p", "q");
        });
    }

    /**
     * The reason the OAuth2/OIDC endpoints are deliberately absent from
     * {@code docs/api/openapi/ukun-auth.yaml}: the server describes itself, so a hand-written copy
     * would only drift. This asserts the document actually exists to be relied on.
     */
    @Test
    @DisplayName("the OIDC discovery document describes the endpoints instead of the OpenAPI file")
    void publishesItsOwnDiscoveryDocument() {
        Map<String, Object> discovery = http.get()
                .uri("/.well-known/openid-configuration")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult()
                .getResponseBody();

        assertThat(discovery)
                .containsKeys("issuer", "authorization_endpoint", "token_endpoint", "jwks_uri")
                .extractingByKey("jwks_uri")
                .asString()
                .endsWith("/oauth2/jwks");
    }

    /**
     * The seeder runs on every start and is idempotent by {@code client_id}. Without that, a restart
     * would either fail on the unique index or leave a second row, and which of the two clients
     * answered a token request would become a matter of luck.
     */
    @Test
    @DisplayName("the two clients are registered once, in the database, not in memory")
    void registersBothClientsExactlyOnce() {
        List<String> clientIds = jdbc.queryForList(
                "SELECT client_id FROM oauth2_registered_client ORDER BY client_id", String.class);

        assertThat(clientIds).containsExactly("ukun-api", "ukun-gateway");
    }

    /** The gateway is a public-facing client; it must not be able to mint tokens without a person. */
    @Test
    @DisplayName("the gateway cannot use client credentials, only the authorization code flow")
    void theGatewayIsNotAllowedToSkipTheUser() {
        http.post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth("ukun-gateway", GATEWAY_SECRET))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials")
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

}
