package dev.sandoretti.ukun.gateway.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Builds the session's principal from the id token <em>and</em> the access token, because the two
 * carry different things and this gateway needs one from each.
 *
 * <h2>Why the id token is not enough</h2>
 *
 * {@code TokenClaimsCustomizer} in {@code ukun-auth} rewrites {@code sub} and adds {@code email} and
 * {@code roles} only for {@code OAuth2TokenType.ACCESS_TOKEN}; it returns early for the id token,
 * which therefore keeps the defaults — no {@code roles} at all, and a {@code sub} that is the email,
 * because that is what a person types into the login form.
 *
 * <p>So a principal built from the id token alone would carry no {@code ROLE_*} authority, and the
 * coarse filter of ADR-0020 would answer 403 to every real administrator on {@code /api/admin/**}.
 * That is not a rule that can be relaxed here: it is the one rule this layer has.
 *
 * <h2>Why the access token, specifically</h2>
 *
 * Not merely because it is the token that happens to have the claims. It is the <em>same credential
 * this gateway relays</em> to {@code ukun-api} ({@code TokenRelay}), so a decision taken from it can
 * never disagree with the one the resource server will take from it one hop later. Deriving the edge
 * decision from a different token than the one forwarded is how a coarse filter and its authority
 * drift apart, and {@code docs/08} §4 is explicit that the API is the authority — the edge's job is
 * to be a cheap, consistent echo of it, never a second opinion.
 *
 * <p>This is not the gateway acting as a resource server. It validates a token addressed to somebody
 * else ({@code aud: ukun-api}) and deliberately does not check that audience — it is reading its own
 * session material, not accepting a bearer credential from a caller.
 */
@Component
class AccessTokenOidcUserService implements ReactiveOAuth2UserService<OidcUserRequest, OidcUser> {

    /** {@code docs/08-identidad-y-seguridad.md} §4. Names, not enum instances, as minted. */
    private static final String ROLES_CLAIM = "roles";

    /** What {@code hasRole("ADMIN")} expands to. Spring strips it; the authority carries it. */
    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * Standard OIDC behaviour on top: with no {@code userInfoUri} configured it builds the user from
     * the id token and makes no further call ({@link ClientRegistrationConfig}).
     */
    private final OidcReactiveOAuth2UserService delegate = new OidcReactiveOAuth2UserService();

    private final ReactiveJwtDecoder accessTokens;

    AccessTokenOidcUserService(UkunOAuth2Properties properties) {
        NimbusReactiveJwtDecoder decoder =
                NimbusReactiveJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        // Signature, expiry and issuer. Not the audience, which names ukun-api — see the class note.
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));

        this.accessTokens = decoder;
    }

    @Override
    public Mono<OidcUser> loadUser(OidcUserRequest userRequest) {
        return delegate.loadUser(userRequest).flatMap(user -> accessTokens
                .decode(userRequest.getAccessToken().getTokenValue())
                .onErrorMap(JwtException.class, AccessTokenOidcUserService::unusableAccessToken)
                .map(accessToken -> withClaimsFrom(user, accessToken, userRequest)));
    }

    private static UkunOidcUser withClaimsFrom(OidcUser user, Jwt accessToken, OidcUserRequest request) {
        String nameAttribute = request.getClientRegistration()
                .getProviderDetails()
                .getUserInfoEndpoint()
                .getUserNameAttributeName();

        return new UkunOidcUser(
                allAuthoritiesOf(user, accessToken), user.getIdToken(), nameAttribute, accessToken.getSubject());
    }

    /**
     * The delegate's own authorities are kept, not replaced: they are the {@code SCOPE_*} ones and
     * {@code OIDC_USER}, and something downstream may yet want to reason about scope. The roles are
     * added on top.
     */
    static Collection<GrantedAuthority> allAuthoritiesOf(OidcUser user, Jwt accessToken) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>(user.getAuthorities());
        rolesOf(accessToken).forEach(role -> authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role)));

        return authorities;
    }

    /**
     * A token without the claim yields no roles rather than an error. That is the safe direction: the
     * caller ends up authenticated with no {@code ROLE_*}, which the coarse filter refuses, instead of
     * the login failing in a way that looks like the authorization server being down.
     */
    private static List<String> rolesOf(Jwt accessToken) {
        List<String> roles = accessToken.getClaimAsStringList(ROLES_CLAIM);
        return roles == null ? List.of() : roles;
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
