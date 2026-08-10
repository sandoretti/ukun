package dev.sandoretti.ukun.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The key the access token is signed with, and the one {@code /oauth2/jwks} publishes so that
 * {@code ukun-api} can validate a token without ever calling this service (issue #24).
 *
 * <p><strong>Generated at startup, and that is a decision with a visible consequence:</strong>
 * restarting {@code ukun-auth} invalidates every token already issued, so a {@code tilt up} reload
 * logs everybody out. That is acceptable for v0.4 and it is the reason the key sits behind its own
 * bean rather than inline in the server configuration — v1.0 replaces this method with one that
 * reads a key from configuration or a keystore, and nothing else in the service changes.
 *
 * <p>Declared explicitly instead of leaning on Boot's autoconfiguration, which would provide an
 * equivalent bean silently. A signing key is not a detail to discover by reading someone else's
 * defaults.
 */
@Configuration(proxyBeanMethods = false)
class JwkSourceConfig {

    @Bean
    JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();

        RSAKey key = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();

        return new ImmutableJWKSet<>(new JWKSet(key));
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JVM has no RSA key pair generator", e);
        }
    }
}
