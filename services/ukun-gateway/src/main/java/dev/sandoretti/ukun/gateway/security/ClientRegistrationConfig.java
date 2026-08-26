package dev.sandoretti.ukun.gateway.security;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * The client registration of the BFF, built by hand.
 *
 * <h2>Why not {@code spring.security.oauth2.client.registration.*}</h2>
 *
 * Because those properties cannot express this deployment. Boot's
 * {@code OAuth2ClientPropertiesMapper} calls {@code ClientRegistrations.fromIssuerLocation(...)} the
 * moment a {@code provider.*.issuer-uri} is present — an OIDC discovery call made <em>at
 * startup</em>, from inside the container network, against a URL only the browser can resolve
 * ({@code http://localhost:9000}). Pointing it at {@code http://ukun-auth:9000} instead does not
 * help: that method requires the {@code issuer} in the returned document to equal the location it
 * asked, and {@code ukun-auth} publishes the browser-facing one. Either way the service does not
 * start.
 *
 * <p>Dropping {@code issuer-uri} and listing the endpoints as properties avoids the call but loses
 * the issuer check on the id token — {@code OidcIdTokenValidator} compares {@code iss} against
 * {@code ProviderDetails.getIssuerUri()} and skips it when that is absent. Building the registration
 * here keeps both: no discovery, and the {@code iss} still verified.
 *
 * <p>It is the same trade {@code ukun-api} already makes at {@code application.yaml:33-40}, where
 * {@code issuer-uri} and {@code jwk-set-uri} are set side by side precisely so that no discovery ever
 * happens — with the difference that on the resource server side Boot allows it and here it does not.
 *
 * <h2>PKCE is not configured here</h2>
 *
 * The registered client has {@code requireProofKey(true)} (issue #22), and Spring's
 * {@code OAuth2AuthorizationRequestRedirectWebFilter} adds the challenge on its own for any public or
 * confidential client whose server demands it. There is no switch to turn on, and its absence is not
 * an omission — {@code OAuth2LoginIT} asserts the {@code code_challenge} really goes out.
 */
@Configuration(proxyBeanMethods = false)
class ClientRegistrationConfig {

    /**
     * Not a free choice: {@code ukun-auth} registered the redirect URI as
     * {@code ${UKUN_GATEWAY_BASE_URL}/login/oauth2/code/ukun} (issue #22), and the last segment of
     * that path <em>is</em> this value. Change one without the other and the authorization server
     * answers {@code invalid_redirect_uri}, with nothing in this service's log to explain it.
     */
    static final String REGISTRATION_ID = "ukun";

    /**
     * The four scopes of {@code docs/08-identidad-y-seguridad.md} §4, matching the registration.
     * {@code openid} is what makes this an OIDC login rather than a bare authorization code grant,
     * and therefore what produces the id token the session is built from.
     */
    private static final String[] SCOPES = {"openid", "profile", "ukun.read", "ukun.write"};

    /**
     * Expanded per request rather than hardcoded, so that a worktree publishing the gateway on
     * another port ({@code .env.ports}, skill {@code worktree}) still sends a redirect URI that
     * matches the one registered — both derive from the same base.
     */
    private static final String REDIRECT_URI = "{baseUrl}/login/oauth2/code/{registrationId}";

    @Bean
    ReactiveClientRegistrationRepository clientRegistrations(UkunOAuth2Properties properties) {
        return new InMemoryReactiveClientRegistrationRepository(ukun(properties));
    }

    /**
     * Reads the <em>access</em> token of the session — the credential this gateway relays, and the
     * only place the roles and the account id live ({@code TokenClaimsCustomizer} in {@code ukun-auth}
     * returns early for the id token, which therefore carries neither).
     *
     * <p>It is a bean rather than a field of its one-time owner because two collaborators now read
     * that token and <strong>must</strong> read it the same way: {@link RelayedTokenAuthorizationManager},
     * which decides {@code /api/admin/**} on every request, and {@link AccessTokenOidcUserService},
     * which takes the account id off it once at login. Two decoders would be two readings of one
     * credential, which is the shape of the defect issue #145 records.
     *
     * <h2>Signature, expiry and issuer — not the audience</h2>
     *
     * The audience names {@code ukun-api}, and skipping it is deliberate: this service is validating
     * material it holds in its own session, not accepting a bearer credential presented by a caller.
     * Checking it would reject every token this gateway legitimately relays. That it is a token
     * addressed to somebody else is precisely why the gateway is not a resource server.
     */
    @Bean
    ReactiveJwtDecoder accessTokenDecoder(UkunOAuth2Properties properties) {
        NimbusReactiveJwtDecoder decoder =
                NimbusReactiveJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));

        return decoder;
    }

    private static ClientRegistration ukun(UkunOAuth2Properties properties) {
        return ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientName("Ukun BFF")
                .clientId(properties.clientId())
                .clientSecret(properties.secret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(REDIRECT_URI)
                .scope(SCOPES)
                .issuerUri(properties.issuer())
                .authorizationUri(properties.authorizationUri())
                .tokenUri(properties.tokenUri())
                .jwkSetUri(properties.jwkSetUri())
                // No userInfoUri, and that is a decision. With the openid scope Spring builds the
                // principal from the id token alone unless a userinfo endpoint is configured, and
                // asking ukun-auth for claims it already signed into that token would only add a
                // network hop to every login. What the id token does NOT carry — the roles — comes
                // from the access token instead: see AccessTokenOidcUserService.
                .userNameAttributeName(IdTokenClaimNames.SUB)
                // Nothing reads this from a discovery document, because none is ever fetched, and
                // OidcClientInitiatedServerLogoutSuccessHandler looks it up here by exactly this key.
                .providerConfigurationMetadata(Map.of("end_session_endpoint", properties.endSessionUri()))
                .build();
    }
}
