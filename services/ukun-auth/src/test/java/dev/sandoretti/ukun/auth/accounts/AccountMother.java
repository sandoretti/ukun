package dev.sandoretti.ukun.auth.accounts;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Accounts for the tests, built explicitly rather than fished out of seed data — the {@code testing}
 * skill's reason for object mothers, and here also a practical one: the demo accounts are issue #23
 * and do not exist yet.
 *
 * <p>It writes through JDBC and not through a repository so that the row under test is exactly what
 * is asked for, including the states production code refuses to create — a disabled account, an
 * account with no role at all.
 *
 * <p>The password is hashed with the real {@link PasswordEncoder}. Inserting a plaintext one would
 * make every login test pass against a service that had silently stopped hashing, which is the one
 * regression this service cannot afford.
 */
public final class AccountMother {

    /** The customer of the happy path. */
    public static final String CUSTOMER_EMAIL = "customer@ukun.dev";

    /** Twelve characters, the NIST minimum of docs/08 §8 — not a policy test, just not a bad example. */
    public static final String PASSWORD = "correct-horse-battery";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public AccountMother(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    public UUID customer() {
        return account(CUSTOMER_EMAIL, PASSWORD, true, Role.CUSTOMER);
    }

    /** An employee who is also an admin: the one case ADR-0006 allows two roles at once. */
    public UUID admin(String email) {
        return account(email, PASSWORD, true, Role.EMPLOYEE, Role.ADMIN);
    }

    /** Reserved by the signup saga and never activated (skill {@code spring-auth}, rule 4). */
    public UUID reserved(String email) {
        return account(email, PASSWORD, false, Role.CUSTOMER);
    }

    public UUID account(String email, String rawPassword, boolean enabled, Role... roles) {
        UUID id = UUID.randomUUID();

        jdbc.update(
                "INSERT INTO account (id, email, password_hash, enabled) VALUES (?, ?, ?, ?)",
                id,
                email,
                passwordEncoder.encode(rawPassword),
                enabled);

        for (Role role : roles) {
            jdbc.update("INSERT INTO account_role (account_id, role) VALUES (?, ?)", id, role.name());
        }

        return id;
    }
}
