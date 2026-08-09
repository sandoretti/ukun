package dev.sandoretti.ukun.auth.accounts.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The password hash, which ADR-0017 §4 places in {@code accounts} — it belongs next to the column it
 * writes.
 *
 * <p>There is nothing to "fix later" here, and that is the whole point: the legacy stores
 * {@code cuenta.contrasenna} in the clear in a {@code VARCHAR(16)} and compares it with a string
 * equality, so every account in it is already compromised. {@code ukun-auth} hashes from its first
 * commit (skill {@code spring-auth}, rule 1).
 *
 * <p>{@link PasswordEncoderFactories#createDelegatingPasswordEncoder()} rather than a bare
 * {@code new BCryptPasswordEncoder()}: it stores the algorithm as a {@code {bcrypt}} prefix in the
 * hash itself, so migrating to Argon2id later is a change of default encoder that keeps verifying
 * every existing hash — instead of a flag day. {@code password_hash} is {@code VARCHAR(255)} in the
 * baseline with exactly that headroom.
 *
 * <p>It also encodes the two OAuth2 client secrets: Spring's client authentication matches them
 * through this same bean, so a secret at rest is a hash here too.
 */
@Configuration(proxyBeanMethods = false)
class PasswordEncoderConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
