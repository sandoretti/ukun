package dev.sandoretti.ukun.auth.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.authentication.password.CompromisedPasswordDecision;
import org.springframework.security.web.authentication.password.HaveIBeenPwnedRestApiPasswordChecker;

/**
 * The breach-corpus half of {@link dev.sandoretti.ukun.auth.application.PasswordPolicy}, which
 * {@code docs/08-identidad-y-seguridad.md} §8 asks for by name: Have I Been Pwned, queried with
 * k-anonymity.
 *
 * <p><strong>k-anonymity is why this is not a privacy problem.</strong> The password is SHA-1'd
 * locally and only the first five hex characters of the digest leave the process; the API answers
 * with every suffix under that prefix and the comparison happens here. The remote end learns that
 * somebody is checking one of a few hundred thousand hashes, which is nothing about this user and
 * nothing about their password.
 *
 * <h2>Two switches, and what each one is for</h2>
 *
 * <p>Spring's checker <strong>fails open</strong>: it catches {@code RestClientException}, logs, and
 * answers "not compromised". That is a deliberate trade rather than an oversight to work around — a
 * third party's availability does not get to decide whether people can register with Ukun. The
 * failure is visible in the log, and the other two rules of the policy still hold.
 *
 * <p>{@code ukun.password.compromised-check.enabled=false} replaces it with a checker that approves
 * everything. It exists for the test suite: an {@code *IT} does not reach the internet
 * ({@code docs/13-testing.md} §1), and a suite whose result depends on a remote host is a suite that
 * fails for reasons that have nothing to do with the change under test. It defaults to {@code true},
 * so switching it off is something a deployment has to do on purpose.
 */
@Configuration(proxyBeanMethods = false)
class CompromisedPasswordConfig {

    private static final String ENABLED = "ukun.password.compromised-check.enabled";

    @Bean
    @ConditionalOnProperty(name = ENABLED, havingValue = "true", matchIfMissing = true)
    CompromisedPasswordChecker haveIBeenPwnedPasswordChecker() {
        return new HaveIBeenPwnedRestApiPasswordChecker();
    }

    /**
     * The disabled variant. It approves every password rather than rejecting every password, because
     * the alternative would make the switch unusable: a test that turns the check off wants the other
     * two rules of the policy exercised, not a service where no password is acceptable.
     */
    @Bean
    @ConditionalOnProperty(name = ENABLED, havingValue = "false")
    CompromisedPasswordChecker permitAllPasswordChecker() {
        return password -> new CompromisedPasswordDecision(false);
    }
}
