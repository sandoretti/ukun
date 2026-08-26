package dev.sandoretti.ukun.gateway.security;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Builds the session's principal from the id token <em>and</em> the access token, because the account
 * id this service logs by lives only in the second one.
 *
 * <h2>What the id token does not carry</h2>
 *
 * {@code TokenClaimsCustomizer} in {@code ukun-auth} rewrites {@code sub} and adds {@code email} and
 * {@code roles} only for {@code OAuth2TokenType.ACCESS_TOKEN}; it returns early for the id token,
 * which therefore keeps the defaults — no {@code roles} at all, and a {@code sub} that is the email,
 * because that is what a person types into the login form.
 *
 * <p>So the principal takes its {@link UkunOidcUser#accountId()} from the access token: the UUID
 * {@code docs/11-observabilidad-y-logging.md} §3 requires on the {@code auth.logout} event and the
 * access log, and the one identifier for a person that is not their email ({@code docs/11} §5 keeps
 * email out of the MDC).
 *
 * <h2>Why it does not lift the roles into authorities</h2>
 *
 * It used to, for the coarse filter of ADR-0020 to match on with {@code hasRole("ADMIN")}. That is
 * the defect of issue #145: this service runs once, inside the {@code authorization_code} exchange,
 * and is never consulted again when the token is refreshed — so the authorities were the login's
 * photograph while {@code ukun-api} kept deciding the same question from the refreshed token. The
 * rule now reads that token directly ({@link RelayedTokenAuthorizationManager}).
 *
 * <p>Leaving the {@code ROLE_*} authorities in place anyway would not be harmless: a second source of
 * roles in the session is an invitation to write {@code hasRole} against it, which is exactly how the
 * drift comes back. There is one source, and it is the credential this gateway relays.
 *
 * <p>This is not the gateway acting as a resource server. It reads a token addressed to somebody else
 * ({@code aud: ukun-api}) and deliberately does not check that audience — it is reading its own
 * session material, not accepting a bearer credential from a caller. See the decoder in
 * {@link ClientRegistrationConfig}.
 */
@Component
class AccessTokenOidcUserService implements ReactiveOAuth2UserService<OidcUserRequest, OidcUser> {

    /**
     * Standard OIDC behaviour on top: with no {@code userInfoUri} configured it builds the user from
     * the id token and makes no further call ({@link ClientRegistrationConfig}).
     */
    private final OidcReactiveOAuth2UserService delegate = new OidcReactiveOAuth2UserService();

    private final ReactiveJwtDecoder accessTokens;

    AccessTokenOidcUserService(ReactiveJwtDecoder accessTokenDecoder) {
        this.accessTokens = accessTokenDecoder;
    }

    @Override
    public Mono<OidcUser> loadUser(OidcUserRequest userRequest) {
        return delegate.loadUser(userRequest).flatMap(user -> accessTokens
                .decode(userRequest.getAccessToken().getTokenValue())
                .onErrorMap(JwtException.class, AccessTokenOidcUserService::unusableAccessToken)
                .map(accessToken -> withAccountId(user, accessToken, nameAttributeOf(userRequest))));
    }

    private static String nameAttributeOf(OidcUserRequest request) {
        return request.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();
    }

    /**
     * The delegate's own authorities are carried over unchanged — the {@code SCOPE_*} ones and
     * {@code OIDC_USER}. Nothing is added: see the class note on why no role belongs here.
     */
    static UkunOidcUser withAccountId(OidcUser user, Jwt accessToken, String nameAttribute) {
        return new UkunOidcUser(user.getAuthorities(), user.getIdToken(), nameAttribute, accessToken.getSubject());
    }

    /**
     * Surfaced as an authentication failure rather than a 500. An access token this service cannot
     * validate means the login did not happen, and the browser should be told so at the callback.
     */
    private static OAuth2AuthenticationException unusableAccessToken(JwtException cause) {
        return new OAuth2AuthenticationException(
                new OAuth2Error("invalid_token", "The access token could not be validated", null), cause);
    }
}
