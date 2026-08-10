package dev.sandoretti.ukun.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static dev.sandoretti.ukun.auth.accounts.AccountMother.CUSTOMER_EMAIL;
import static dev.sandoretti.ukun.auth.accounts.AccountMother.PASSWORD;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Logging in, and — more to the point — the three ways of failing to, which
 * {@code docs/08-identidad-y-seguridad.md} §8 requires to be indistinguishable from outside.
 */
class LoginIT extends LoginIntegrationTest {

    @Test
    @DisplayName("a customer with the right password gets an authenticated session")
    void signsInACustomer() {
        accounts().customer();

        LoginAttempt attempt = logIn(CUSTOMER_EMAIL, PASSWORD);

        assertThat(attempt.succeeded()).isTrue();
        assertThat(attempt.sessionCookie()).isNotNull();
    }

    /**
     * The assertion this class exists for. An endpoint that answers differently to "this email does
     * not exist" and "this password is wrong" is an account enumeration oracle: an attacker with a
     * list of addresses learns which of them are customers of this shop without guessing a single
     * password.
     *
     * <p>Comparing the two responses field by field, rather than checking each is "an error", is what
     * makes the test fail if somebody later adds a helpful message to one of them.
     */
    @Test
    @DisplayName("an unknown email and a wrong password are answered identically")
    void failsIdenticallyWhicheverCredentialIsWrong() {
        accounts().customer();

        LoginAttempt unknownEmail = logIn("nobody@ukun.dev", PASSWORD);
        LoginAttempt wrongPassword = logIn(CUSTOMER_EMAIL, "not-the-password");

        assertThat(unknownEmail.succeeded()).isFalse();
        assertThat(wrongPassword.succeeded()).isFalse();
        assertThat(unknownEmail.status()).isEqualTo(wrongPassword.status());
        assertThat(unknownEmail.location()).isEqualTo(wrongPassword.location());
    }

    /**
     * A reserved account is one the signup saga created and never activated. It must not be able to
     * log in — that is the whole point of reserving first (skill {@code spring-auth}, rule 4) — and
     * it must not announce itself as existing either, or the two-step alta becomes a way to check
     * whether an email is taken.
     */
    @Test
    @DisplayName("an account reserved but never activated cannot log in, and does not say so")
    void refusesAReservedAccountWithoutRevealingIt() {
        accounts().reserved("reserved@ukun.dev");
        accounts().customer();

        LoginAttempt reserved = logIn("reserved@ukun.dev", PASSWORD);
        LoginAttempt unknown = logIn("nobody@ukun.dev", PASSWORD);

        assertThat(reserved.succeeded()).isFalse();
        assertThat(reserved.status()).isEqualTo(unknown.status());
        assertThat(reserved.location()).isEqualTo(unknown.location());
    }

    /**
     * The password is stored hashed and nothing else will do. Reading the column back is a blunt
     * assertion, and that is the point: it fails the moment somebody "simplifies" the encoder away,
     * which is the single defect of the legacy that compromises every account at once.
     */
    @Test
    @DisplayName("the stored password is a hash, never the password")
    void storesOnlyAHash() {
        accounts().customer();

        String stored = jdbc.queryForObject(
                "SELECT password_hash FROM account WHERE email = ?", String.class, CUSTOMER_EMAIL);

        assertThat(stored).isNotEqualTo(PASSWORD).doesNotContain(PASSWORD).startsWith("{bcrypt}$2a$");
    }
}
