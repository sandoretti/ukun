package dev.sandoretti.ukun.auth;

import static dev.sandoretti.ukun.auth.AccountMother.CUSTOMER_EMAIL;
import static dev.sandoretti.ukun.auth.AccountMother.PASSWORD;
import static dev.sandoretti.ukun.auth.AuthorizationCodeFlow.GATEWAY_REDIRECT_URI;
import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

        String location = new AuthorizationCodeFlow(http, GATEWAY_SECRET)
                .authorize(session, UriComponentsBuilder.fromPath("/oauth2/authorize")
                .queryParam("response_type", "code")
                        .queryParam("client_id", "ukun-gateway")
                        .queryParam("scope", "openid profile")
                        .queryParam("redirect_uri", GATEWAY_REDIRECT_URI)
                        .build()
                        .encode()
                        .toUri());

        assertThat(location).contains("error=invalid_request");
    }

    /** The whole authorization code + PKCE dance (see {@link AuthorizationCodeFlow}). */
    private SignedJWT userAccessToken(String email, String password) throws ParseException {
        String session = logIn(email, password).sessionCookie();
        var tokens = new AuthorizationCodeFlow(http, GATEWAY_SECRET).tokensFor(session);

        return SignedJWT.parse((String) tokens.get("access_token"));
    }
}
