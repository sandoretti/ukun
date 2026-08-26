package dev.sandoretti.ukun.gateway.support;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

/**
 * Mints the access tokens {@code ukun-auth} would issue, signed with a key this class publishes at
 * the JWKS path of {@link UpstreamStub#AUTH} — the one thing the gateway <em>fetches</em> from the
 * authorization server rather than proxies.
 *
 * <h2>Why real tokens and not a stubbed decoder</h2>
 *
 * Because the claim being tested is not "the gateway reads a claim": it is that the edge decides on
 * the very credential it relays, and a decoder replaced in the test context would be a second reading
 * of the token — exactly the divergence {@code AdminAuthorizationIT} exists to rule out. Signing for
 * real also keeps every {@code *IT} on the one Spring context this module has (skill
 * {@code spring-gateway}): no property is overridden, because {@link UpstreamStub#bind} already
 * points {@code ukun.oauth2.jwk-set-uri} here.
 *
 * <p>Nimbus and the JDK's HTTP server, both already on the classpath through the OAuth2 client
 * starter — no new dependency, and the same choice {@code JwksTestServer} makes in {@code ukun-api}.
 *
 * <p>It names {@link UpstreamStub} and {@link UpstreamStub#bind} names it back. The two halves are
 * one subject — what {@code ukun-auth} is to a test of this service — and splitting the issuer away
 * from the stub that stands for the same server would put the same URL in two files.
 */
public final class AccessTokens {

    /** What the account's {@code sub} looks like: a UUID, never the email ({@code docs/08} §4). */
    public static final String ACCOUNT_ID = "8f2c1e40-3b7a-4d21-9a55-1f0c9d2e6b31";

    private static final RSAKey SIGNING_KEY = generateKey();

    private static final HttpServer JWKS = publish(SIGNING_KEY);

    private AccessTokens() {}

    /** Where {@link UpstreamStub#bind} points {@code ukun.oauth2.jwk-set-uri}. */
    public static String jwkSetUri() {
        return "http://localhost:%d/oauth2/jwks".formatted(JWKS.getAddress().getPort());
    }

    /** A token as minted for {@code ukun-api}, with the roles named as {@code docs/08} §4 mints them. */
    public static OAuth2AccessToken withRoles(List<String> roles) {
        return bearer(claims().claim("roles", roles).build());
    }

    /**
     * A token carrying no {@code roles} at all. Not a malformed token: it is what a session holds
     * once the claim stops arriving, which is the half of the divergence that must not grant access.
     */
    public static OAuth2AccessToken withoutRoles() {
        return bearer(claims().build());
    }

    /**
     * Signed with a key nobody published, and otherwise perfectly shaped: the session still holds a
     * credential and it can no longer be validated, which is one of the two ways a session stops
     * being able to authorise anything (issue #145).
     */
    public static OAuth2AccessToken signedByAnotherKey() {
        return bearer(claims().claim("roles", List.of("EMPLOYEE", "ADMIN")).build(), generateKey());
    }

    /**
     * The issuer is the browser-facing one because that is what the gateway validates against
     * ({@code ukun.oauth2.issuer}); the audience names {@code ukun-api}, and this service
     * deliberately does not check it — it is reading its own session material, not accepting a
     * bearer credential from a caller ({@code AccessTokenOidcUserService}).
     */
    private static JWTClaimsSet.Builder claims() {
        Instant now = Instant.now();

        return new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .issuer(UpstreamStub.BROWSER_FACING_ISSUER)
                .audience("ukun-api")
                .subject(ACCOUNT_ID)
                .claim("email", "employee@ukun.dev")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)));
    }

    private static OAuth2AccessToken bearer(JWTClaimsSet claims) {
        return bearer(claims, SIGNING_KEY);
    }

    private static OAuth2AccessToken bearer(JWTClaimsSet claims, RSAKey key) {
        return new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                sign(claims, key),
                claims.getIssueTime().toInstant(),
                claims.getExpirationTime().toInstant());
    }

    private static String sign(JWTClaimsSet claims, RSAKey key) {
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .type(JOSEObjectType.JWT)
                    // The published key's id, so the decoder picks that one and fails on the
                    // signature rather than on not finding a key — which is the interesting failure.
                    .keyID(SIGNING_KEY.getKeyID())
                    .build();

            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(key));

            return jwt.serialize();

        } catch (Exception e) {
            throw new IllegalStateException("Could not mint an access token", e);
        }
    }

    /**
     * Started once for the whole JVM and stopped by a shutdown hook, for the same reason
     * {@link UpstreamStub} is: every {@code @SpringBootTest} here binds the same properties, so Spring
     * reuses one application context instead of starting one per class.
     */
    private static HttpServer publish(RSAKey key) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            byte[] jwks = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);

            server.createContext("/oauth2/jwks", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                try (var body = exchange.getResponseBody()) {
                    body.write(jwks);
                }
            });
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));

            return server;

        } catch (Exception e) {
            throw new IllegalStateException("Could not publish the test JWK set", e);
        }
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .generate();

        } catch (Exception e) {
            throw new IllegalStateException("Could not generate the signing key", e);
        }
    }
}
