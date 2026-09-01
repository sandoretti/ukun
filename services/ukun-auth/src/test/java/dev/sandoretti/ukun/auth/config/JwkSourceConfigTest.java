package dev.sandoretti.ukun.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import dev.sandoretti.ukun.auth.SigningKeys;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The half of issue #118 that a unit test can prove: two replicas holding the same configured key
 * publish the <em>same</em> JWKS, and restarting one does not change what it publishes.
 *
 * <p>No Spring context here, and that is the point — two instances of this class are two replicas as
 * far as this property is concerned, and asserting it costs neither a container nor a context (skill
 * {@code testing}: every new {@code @SpringBootTest} is one more context and contexts are not
 * closed). What the context-level suites keep asserting is that {@code /oauth2/jwks} serves it.
 */
class JwkSourceConfigTest {

    @Test
    @DisplayName("derives the key id from the key itself, as the RFC 7638 thumbprint")
    void shouldDeriveTheKeyIdFromTheKeyItself() throws Exception {
        RSAKey key = publishedKeyOf(SigningKeys.TEST_KEY);

        assertThat(key.getKeyID()).isEqualTo(key.computeThumbprint().toString());
    }

    @Test
    @DisplayName("publishes the same key id from every instance holding the same key")
    void shouldPublishTheSameKeyIdForEveryInstanceOfTheSameKey() {
        RSAKey oneReplica = publishedKeyOf(SigningKeys.TEST_KEY);
        RSAKey anotherReplica = publishedKeyOf(SigningKeys.TEST_KEY);

        assertThat(oneReplica.getKeyID()).isEqualTo(anotherReplica.getKeyID());
        assertThat(oneReplica.toPublicJWK()).isEqualTo(anotherReplica.toPublicJWK());
    }

    @Test
    @DisplayName("reads the key with PEM armour and line breaks as the same key")
    void shouldReadAnArmouredKeyAsTheSameKey() {
        String armoured = "-----BEGIN PRIVATE KEY-----\n"
                + String.join("\n", SigningKeys.TEST_KEY.split("(?<=\\G.{64})"))
                + "\n-----END PRIVATE KEY-----\n";

        assertThat(publishedKeyOf(armoured).getKeyID()).isEqualTo(publishedKeyOf(SigningKeys.TEST_KEY).getKeyID());
    }

    @Test
    @DisplayName("refuses to start on a key that is not base64")
    void shouldRefuseAKeyThatIsNotBase64() {
        assertThatThrownBy(() -> publishedKeyOf("not a key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ukun.signing-key");
    }

    @Test
    @DisplayName("refuses to start on base64 that is not a PKCS#8 RSA key")
    void shouldRefuseAKeyThatIsNotPkcs8() {
        assertThatThrownBy(() -> publishedKeyOf(Base64.getEncoder().encodeToString("nope".getBytes())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PKCS#8");
    }

    @Test
    @DisplayName("refuses to start on a key too short for RS256")
    void shouldRefuseAKeyShorterThanTheMinimum() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        String tooShort =
                Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());

        assertThatThrownBy(() -> publishedKeyOf(tooShort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2048");
    }

    /**
     * A startup failure is logged, so the message is the one place a misconfigured private key could
     * leak into a log file — which is the defect ADR-0024 exists to remove, not one to introduce
     * while removing it. {@code docs/11-observabilidad-y-logging.md} §4.
     */
    @Test
    @DisplayName("never names the key itself in a failure message")
    void shouldNotRepeatTheKeyInTheFailureMessage() {
        String almostAKey = Base64.getEncoder().encodeToString("secret-looking-bytes".getBytes());

        assertThatThrownBy(() -> publishedKeyOf(almostAKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(almostAKey);
    }

    private static RSAKey publishedKeyOf(String configured) {
        JWKSet published = ((ImmutableJWKSet<?>) new JwkSourceConfig(configured).jwkSource()).getJWKSet();

        return (RSAKey) published.getKeys().getFirst();
    }
}
