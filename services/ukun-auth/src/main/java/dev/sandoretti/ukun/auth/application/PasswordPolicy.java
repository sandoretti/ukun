package dev.sandoretti.ukun.auth.application;

import dev.sandoretti.ukun.auth.error.WeakPasswordException;
import java.nio.charset.StandardCharsets;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.stereotype.Component;

/**
 * The password policy of {@code docs/08-identidad-y-seguridad.md} §8, aligned with NIST SP 800-63B.
 *
 * <p>Three rules, and the two that are <strong>absences</strong> matter as much as the one that is
 * present:
 *
 * <ul>
 *   <li><strong>A real minimum length</strong> (12), because length is what actually costs an
 *       attacker work.
 *   <li><strong>No <em>artificial</em> maximum.</strong> The legacy capped passwords at
 *       {@code VARCHAR(16)} — while storing them in the clear — so a passphrase was impossible and a
 *       breach was total. Here the column stores a hash, whose length does not depend on the
 *       password's, so storage caps nothing. What does cap it is BCrypt itself, at
 *       {@value #MAXIMUM_LENGTH_BYTES} bytes; see below.
 *   <li><strong>No composition rules.</strong> "One uppercase, one digit, one symbol" pushes people
 *       towards {@code Password1!} and its variants, which is a small, predictable corpus. NIST
 *       dropped them for that reason, and this service does not reintroduce them.
 * </ul>
 *
 * <p>What replaces them is the check against a known breach corpus, which rejects
 * {@code correcthorsebatterystaple} — long, no composition rule broken, and in every wordlist — where
 * a rule-based policy would wave it through.
 *
 * <p>It is applied <strong>here and not in Bean Validation</strong> on the request record. A
 * {@code @Size(min = 12)} annotation would cover the length, but the breach check needs a collaborator
 * and, more importantly, this is the service that owns credentials: the policy has to hold for every
 * caller of {@code AccountsService}, including a future password change, not only for the ones that
 * happen to arrive through a validated controller.
 */
@Component
public class PasswordPolicy {

    /**
     * Twelve, from §8. Not configurable: a minimum that can be lowered by an environment variable is
     * a minimum that will be lowered by one, and the number is a decision of that document rather
     * than of a deployment.
     */
    public static final int MINIMUM_LENGTH = 12;

    /**
     * BCrypt's own limit, in <strong>bytes</strong> of UTF-8 — not a policy decision and not the
     * legacy's {@code VARCHAR(16)} in a new disguise.
     *
     * <p>The algorithm ignores everything past its 72nd byte, and Spring Security 7 refuses to encode
     * a longer password rather than silently truncating it. Refusing is the right behaviour: silent
     * truncation would mean two different passwords authenticating the same account. Since it cannot
     * be avoided while the encoder is BCrypt, the choice left is <em>where</em> it surfaces — here, as
     * a {@code 400} naming the field, or four frames deeper as a {@code 500} with a stack trace, which
     * is what this constant was added in response to.
     *
     * <p>It does not conflict with NIST SP 800-63B, which asks that at least 64 characters be
     * supported: 72 bytes is 72 ASCII characters. Migrating to Argon2id would remove the limit
     * altogether, and the {@code DelegatingPasswordEncoder} already makes that a change of default
     * rather than a flag day — but it is a decision about credentials and belongs in an ADR, not in
     * this slice.
     */
    public static final int MAXIMUM_LENGTH_BYTES = 72;

    private final CompromisedPasswordChecker compromisedPasswords;

    PasswordPolicy(CompromisedPasswordChecker compromisedPasswords) {
        this.compromisedPasswords = compromisedPasswords;
    }

    /**
     * @throws WeakPasswordException if the password is too short or known to be compromised. It never
     *     returns a boolean: a caller can forget to check one, and cannot forget to handle an
     *     exception that propagates out of its own transaction.
     */
    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MINIMUM_LENGTH) {
            throw WeakPasswordException.tooShort(MINIMUM_LENGTH);
        }
        // Bytes, not characters: an accented passphrase reaches the limit sooner than an ASCII one,
        // and it is the byte count BCrypt counts.
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_LENGTH_BYTES) {
            throw WeakPasswordException.tooLong(MAXIMUM_LENGTH_BYTES);
        }
        if (compromisedPasswords.check(rawPassword).isCompromised()) {
            throw WeakPasswordException.compromised();
        }
    }
}
