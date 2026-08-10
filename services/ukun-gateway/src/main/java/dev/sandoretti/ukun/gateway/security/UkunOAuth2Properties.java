package dev.sandoretti.ukun.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The one OAuth2 client this service is: the BFF of ADR-0003, authorization code with PKCE on behalf
 * of a person. It has its own prefix instead of Boot's
 * {@code spring.security.oauth2.client.registration.*} for a reason that is not stylistic — see
 * {@link ClientRegistrationConfig}, which explains why those properties cannot be used here at all.
 *
 * <h2>Three of these are browser-facing and two are not</h2>
 *
 * {@link #issuer} and {@link #authorizationUri} are followed by <em>the browser</em>, so they must be
 * reachable from the host; {@link #tokenUri} and {@link #jwkSetUri} are fetched by <em>this
 * process</em>, from inside the container network, and naming them {@code localhost} would work on a
 * developer's machine and nowhere else. It is the same split {@code ukun-api} already carries in its
 * {@code application.yaml}, down to the environment variable names, so that a shifted checkout is
 * configured once and both services agree.
 *
 * <p>Everything is a {@code String} rather than a {@code URI}, unlike the sibling
 * {@code UkunGatewayProperties} where the values are route destinations. Here they are fed straight
 * to Spring Security as strings, and {@link #issuer} in particular is compared <em>literally</em>
 * against the {@code iss} claim of the id token: a round trip through {@code URI} could normalise it
 * into a value that no longer matches what the authorization server minted.
 */
@ConfigurationProperties(prefix = "ukun.oauth2")
public record UkunOAuth2Properties(

        /** {@code ukun-gateway}, as registered by {@code ukun-auth} (issue #22). Not a secret. */
        String clientId,

        /** This one is. No default anywhere: a service that starts with a well-known secret is worse than one that refuses to start. */
        String secret,

        /** The {@code iss} of every token, and what {@code OidcIdTokenValidator} compares against. Browser-facing. */
        String issuer,

        /** Where the browser is sent to log in. Browser-facing. */
        String authorizationUri,

        /** Where this process exchanges the code, and later refreshes. Container-facing. */
        String tokenUri,

        /** Where this process fetches the keys that validate the id token. Container-facing. */
        String jwkSetUri,

        /** RP-initiated logout. Browser-facing, and absent from any discovery document we ever read. */
        String endSessionUri,

        /**
         * Where {@code ukun-auth} sends the browser back after logout. It has to match the URI
         * registered in issue #22 <strong>exactly</strong>, trailing slash included — the
         * authorization server compares {@code post_logout_redirect_uri} as a literal, so
         * {@code {baseUrl}} resolving to a slash-less origin would be rejected.
         */
        String postLogoutRedirectUri) {

    /**
     * Hand-written for the same reason as {@code RegisteredClientProperties.Client} in
     * {@code ukun-auth}: {@code docs/11-observabilidad-y-logging.md} §4 forbids a secret in a log
     * line, and Boot prints bound configuration properties at {@code DEBUG} — a record's generated
     * {@code toString()} would print this one in full. The ArchUnit rule that catches this shape only
     * looks at {@code *Request}/{@code *Command} types, so nothing would have failed the build.
     */
    @Override
    public String toString() {
        return "UkunOAuth2Properties[clientId=%s, issuer=%s, secret=***]".formatted(clientId, issuer);
    }
}
