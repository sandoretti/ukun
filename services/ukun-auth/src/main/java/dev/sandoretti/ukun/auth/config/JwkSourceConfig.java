package dev.sandoretti.ukun.auth.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The key the access token is signed with, and the one {@code /oauth2/jwks} publishes so that
 * {@code ukun-api} can validate a token without ever calling this service (issue #24).
 *
 * <p><strong>It comes from configuration, and that is the whole point</strong> (ADR-0024, issue
 * #118). Until v1.0 this method generated a fresh key pair on every startup, which cost two things:
 * two replicas behind a balancer published two different JWKS and produced intermittent {@code 401};
 * and — with a single replica, which is what makes this a defect and not a scaling limitation — every
 * deployment invalidated every token already issued.
 *
 * <h2>No default value, on purpose</h2>
 *
 * {@code ukun.signing-key} has no fallback, exactly like the two client secrets next to it in
 * {@code application.yaml}: a service that starts with a key somebody could guess is worse than one
 * that refuses to start. {@code .env.example} carries the command that generates one.
 *
 * <h2>The key id is derived, not configured</h2>
 *
 * {@code kid} is the RFC 7638 thumbprint of the public key, so two replicas holding the same key
 * publish the same {@code kid} without agreeing on anything, and changing the key changes the
 * {@code kid} by itself. A configured {@code kid} would be one more thing that has to match across
 * replicas, and a random one is what this class did before.
 *
 * <p><strong>Rotation with overlapping {@code kid}s is deliberately not here</strong> (ADR-0024): a
 * {@code JWKSet} holding the new key and still publishing the old one is the right way to rotate
 * without closing sessions, and it buys nothing until there is a rotation to manage.
 */
@Configuration(proxyBeanMethods = false)
class JwkSourceConfig {

    /**
     * RS256 over a shorter modulus is rejected by the JOSE layer at signing time, which is a long way
     * from the configuration that caused it. Failing here names the property instead.
     */
    private static final int MINIMUM_MODULUS_BITS = 2048;

    private final String signingKey;

    JwkSourceConfig(@Value("${ukun.signing-key}") String signingKey) {
        this.signingKey = signingKey;
    }

    @Bean
    JWKSource<SecurityContext> jwkSource() {
        RSAPrivateCrtKey privateKey = readPkcs8(signingKey);
        RSAPublicKey publicKey = publicKeyOf(privateKey);

        try {
            RSAKey key = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyIDFromThumbprint()
                    .build();

            return new ImmutableJWKSet<>(new JWKSet(key));

        } catch (JOSEException e) {
            throw new IllegalStateException("Could not derive the key id of ukun.signing-key", e);
        }
    }

    /**
     * Accepts the PKCS#8 key either as the bare base64 of its DER — which is what fits on one line of
     * a {@code .env} file — or with the PEM armour and line breaks around it, which is what comes out
     * of {@code openssl} without further piping. Both reach the same bytes, and refusing one of the
     * two would only mean an error message about whitespace.
     *
     * <p>No exception message here ever contains {@link #signingKey}: a startup failure is logged, and
     * a private key in a log is the defect this whole slice exists to remove.
     */
    private static RSAPrivateCrtKey readPkcs8(String configured) {
        String base64 = configured
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] der;
        try {
            der = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ukun.signing-key is not valid base64", e);
        }

        PrivateKey key;
        try {
            key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("ukun.signing-key is not a PKCS#8 RSA private key", e);
        }

        // The CRT variant is not a preference: the public key is rebuilt from the modulus and the
        // public exponent below, and only this interface exposes the second one.
        if (!(key instanceof RSAPrivateCrtKey crtKey)) {
            throw new IllegalStateException("ukun.signing-key must be a PKCS#8 RSA private key in CRT form");
        }
        if (crtKey.getModulus().bitLength() < MINIMUM_MODULUS_BITS) {
            throw new IllegalStateException("ukun.signing-key must be at least " + MINIMUM_MODULUS_BITS + " bits");
        }
        return crtKey;
    }

    /**
     * Derived from the private key rather than configured beside it. A public key that has to be
     * supplied separately is a public key that can be supplied wrong, and the failure would be
     * tokens this service signs and nobody can validate.
     */
    private static RSAPublicKey publicKeyOf(RSAPrivateCrtKey privateKey) {
        try {
            RSAPublicKeySpec spec = new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent());
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not derive the public key of ukun.signing-key", e);
        }
    }
}
