package dev.sandoretti.ukun.api;

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
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * A stand-in for ukun-auth's JWKS endpoint, plus the ability to mint tokens signed with the key it
 * publishes.
 *
 * <p>Why not the real service: a resource server is exercised by the tokens it is given, and the ones
 * that matter here are the tokens ukun-auth would never issue — a wrong audience, a foreign issuer, an
 * expired one, a valid-looking one signed by somebody else. Standing up the Authorization Server would
 * make those <em>harder</em> to produce, not easier, and would tie this test to a second service's
 * startup.
 *
 * <p>It is the JDK's own HTTP server and Nimbus, both already on the classpath through the resource
 * server starter — no new dependency, and nothing here that a production profile could ever load.
 *
 * <p>It sits beside {@link AbstractIntegrationTest} rather than in the package of the first test that
 * needed it, and for the same reason that class does: from v0.4 on, every {@code *IT} of a protected
 * endpoint needs a token, so this is shared test infrastructure and not {@code identity}'s alone.
 */
public final class JwksTestServer implements AutoCloseable {

    public static final String ISSUER = "https://auth.test.ukun.dev";
    public static final String AUDIENCE = "ukun-api";

    private final HttpServer server;
    private final RSAKey signingKey;

    private JwksTestServer(HttpServer server, RSAKey signingKey) {
        this.server = server;
        this.signingKey = signingKey;
    }

    public static JwksTestServer start() {
        try {
            RSAKey key = new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .generate();

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

            return new JwksTestServer(server, key);

        } catch (Exception e) {
            throw new IllegalStateException("Could not start the JWKS test server", e);
        }
    }

    public String jwkSetUri() {
        return "http://localhost:%d/oauth2/jwks".formatted(server.getAddress().getPort());
    }

    /** A token this service should accept: our issuer, our audience, an account and its roles. */
    public String validToken(UUID accountId, String email, List<String> roles) {
        return token(builder -> builder.issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(accountId.toString())
                .claim("email", email)
                .claim("roles", roles));
    }

    /** Signed with a key nobody published: the shape is right and the signature is not. */
    public String tokenSignedByAnotherKey(UUID accountId) {
        try {
            RSAKey other = new RSAKeyGenerator(2048).keyID(signingKey.getKeyID()).generate();

            return sign(
                    other,
                    baseClaims()
                            .issuer(ISSUER)
                            .audience(AUDIENCE)
                            .subject(accountId.toString())
                            .build());

        } catch (Exception e) {
            throw new IllegalStateException("Could not mint a foreign-signed token", e);
        }
    }

    public String token(UnaryOperator<JWTClaimsSet.Builder> claims) {
        return sign(signingKey, claims.apply(baseClaims()).build());
    }

    private static JWTClaimsSet.Builder baseClaims() {
        Instant now = Instant.now();

        return new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)));
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) {
        try {
            var header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(key.getKeyID())
                    .type(JOSEObjectType.JWT)
                    .build();

            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(key.toPrivateKey()));

            return jwt.serialize();

        } catch (Exception e) {
            throw new IllegalStateException("Could not sign the test token", e);
        }
    }

    @Override
    public void close() throws IOException {
        server.stop(0);
    }
}
