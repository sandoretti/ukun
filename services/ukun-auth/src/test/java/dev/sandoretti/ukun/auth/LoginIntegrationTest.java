package dev.sandoretti.ukun.auth;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The fixture the three login suites share: a running Authorization Server, a real Postgres, and
 * accounts written by an object mother rather than seeded.
 *
 * <p>The Spring annotations live here rather than on each subclass on purpose — the context cache key
 * includes the {@code @DynamicPropertySource} methods, so three subclasses declaring their own would
 * start three application contexts and run Liquibase three times for one identical configuration.
 *
 * <p>The mechanics of a form login live in {@link FormLogin}, which {@link DemoAccountsIT} also uses:
 * that one asserts the same thing about accounts nobody wrote by hand, and needs a fixture that is
 * the opposite of this one in its three defining choices — the seeded database instead of this one,
 * the {@code demo} context instead of {@code prod}, and no truncation.
 */
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.liquibase.contexts=prod",
            "ukun.oauth2.gateway.secret=" + AbstractIntegrationTest.GATEWAY_SECRET,
            "ukun.oauth2.api.secret=" + AbstractIntegrationTest.API_SECRET
        })
abstract class LoginIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, ACCOUNTS_DATABASE);
    }

    @Autowired
    protected RestTestClient http;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    protected AccountMother accounts() {
        return new AccountMother(jdbc, passwordEncoder);
    }

    /** A form login attempt, driven through the real HTML page. See {@link FormLogin}. */
    protected LoginAttempt logIn(String email, String password) {
        return new FormLogin(http).logIn(email, password);
    }

    /** Ordered truncation rather than {@code @DirtiesContext}, per the `testing` skill. */
    @AfterEach
    void clearAccounts() {
        jdbc.execute("TRUNCATE TABLE account, oauth2_authorization RESTART IDENTITY CASCADE");
    }
}
