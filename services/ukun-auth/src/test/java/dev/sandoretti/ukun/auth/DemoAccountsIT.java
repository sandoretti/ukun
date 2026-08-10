package dev.sandoretti.ukun.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The demo accounts, as Liquibase leaves them under the {@code demo} context.
 *
 * <p>It is the counterpart of {@link SchemaBaselineIT}, which asserts from the {@code prod} side that
 * none of this ever reaches a production database. Between the two, both failure modes of a seeded
 * credential are covered: absent where it is needed, and present where it must never be.
 *
 * <p>Everything here is asserted against rows nobody wrote by hand — unlike the rest of this suite,
 * which builds its accounts with {@link AccountMother}. That is the whole point: an object mother
 * hashes with the real encoder because the test asked it to, whereas these hashes were pasted into a
 * CSV, and the assertion that they verify is the only thing standing between a typo there and a
 * local environment where nobody can log in.
 */
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.liquibase.contexts=demo",
            "ukun.oauth2.gateway.secret=" + DemoAccountsIT.GATEWAY_SECRET,
            "ukun.oauth2.api.secret=" + DemoAccountsIT.API_SECRET
        })
class DemoAccountsIT extends AbstractIntegrationTest {

    static final String GATEWAY_SECRET = "test-gateway-secret";
    static final String API_SECRET = "test-api-secret";

    /**
     * The database of its own that {@link AbstractIntegrationTest} reserves for this class. Sharing
     * one with the rest of the suite would let the order the classes happen to run in decide whether
     * this class or {@link SchemaBaselineIT} fails.
     */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, SEEDED_DATABASE);
    }

    /**
     * Written down once here, and once in {@code docs/06-entorno-local-tilt.md} for whoever has to
     * type it. Deliberately nowhere else — above all not in the changelog, which carries only hashes.
     */
    private static final String DEMO_PASSWORD = "ukun-demo-2026";

    /**
     * The contract between the two databases, spelled out. These fourteen ids are decided in ukun_app's
     * demo data ({@code services/ukun-api/src/main/resources/db/changelog/demo/001-datos-demo.yaml},
     * changeset {@code demo-07-customers}, and {@code demo/data/employees.csv}) and copied into this
     * service's seed; {@code customer.account_id} and {@code employee.account_id} have no foreign key
     * to them because they point into another database (docs/08 §1).
     *
     * <p>Repeating them here rather than deriving them is the assertion: a copy that drifts from its
     * source raises no error anywhere in production code, it just makes a demo login resolve to
     * nobody. Somebody has to hold the two lists side by side, and this is where it happens.
     */
    private static final Set<UUID> EXPECTED_ACCOUNT_IDS = Set.of(
            // The two customers of demo-07-customers.
            UUID.fromString("229b3f0d-771d-4052-b5d6-bc35276e17af"),
            UUID.fromString("32eea2ae-79d5-4fe5-9efd-2a123d8daded"),
            // The twelve employees of data/employees.csv, three per store.
            UUID.fromString("7f0b72e5-3d81-4fcd-8314-49746fd92424"),
            UUID.fromString("7aa0572f-0500-4f88-9456-8d1bba447a9b"),
            UUID.fromString("ae64b655-cde4-45b3-a867-ad60700bf521"),
            UUID.fromString("80fe7045-0a2e-4cd2-9f02-d996c6bc9cc4"),
            UUID.fromString("29f8c753-174f-43da-b204-413b0db89418"),
            UUID.fromString("8e2a8b15-8717-4ce5-83e0-137fc10bb081"),
            UUID.fromString("5b1699d4-03d3-4520-98f0-f7206045dfc7"),
            UUID.fromString("e1467cb8-c2aa-41a7-906f-4f52b755eba8"),
            UUID.fromString("0fc6a2dc-aae9-4a22-aaea-38ca5d690362"),
            UUID.fromString("ec253a9c-df95-4df6-96af-91677ad987f8"),
            UUID.fromString("646bc329-05a3-4cd2-95d2-05b109d54ff9"),
            UUID.fromString("1058b6c6-4925-4b76-834c-9fe7b6b021ad"));

    /** One account per business role, to log in with. The third is an employee who is also admin. */
    private static final String CUSTOMER = "joel.sandoval@email.com";

    private static final String EMPLOYEE = "laura.martinez@ukun.com";

    private static final String ADMIN = "salvador.oton@ukun.com";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RestTestClient http;

    /**
     * {@code enabled} defaults to {@code false} in the baseline because an account is normally
     * reserved first and activated second (skill {@code spring-auth}, rule 4). Seed data is the one
     * case with no saga behind it, so the changeset states the column on every row — and if that is
     * ever dropped, this is the assertion that says why nobody can log in.
     */
    @Test
    @DisplayName("the demo context seeds fourteen accounts and every one of them is enabled")
    void seedsFourteenEnabledAccounts() {
        assertThat(countOf("account")).isEqualTo(14);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account WHERE NOT enabled", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("the seeded ids are exactly the ones ukun_app's demo data points at")
    void matchesTheAccountIdsOfTheOtherDatabase() {
        Set<UUID> seeded = Set.copyOf(jdbc.queryForList("SELECT id FROM account", UUID.class));

        assertThat(seeded).isEqualTo(EXPECTED_ACCOUNT_IDS);
    }

    /**
     * The assertion this class exists for. The hashes were produced by the delegating encoder of
     * {@code PasswordEncoderConfig} and pasted into a CSV, so nothing but this verifies that what
     * ended up in the column is a hash of the password anybody will actually type. Checking the
     * {@code {bcrypt}} prefix would not: a truncated or transposed hash carries it too.
     */
    @Test
    @DisplayName("every seeded hash verifies against the documented demo password")
    void hashesVerifyAgainstTheDemoPassword() {
        List<Map<String, Object>> accounts = jdbc.queryForList("SELECT email, password_hash FROM account");

        assertThat(accounts).hasSize(14).allSatisfy(account -> assertThat(
                        passwordEncoder.matches(DEMO_PASSWORD, (String) account.get("password_hash")))
                .describedAs("the hash of %s does not verify", account.get("email"))
                .isTrue());
    }

    /**
     * Eighteen rows for fourteen accounts: admin accumulates on top of EMPLOYEE instead of replacing
     * it (ADR-0006), so the four store managers of RF-12 carry two rows each.
     */
    @Test
    @DisplayName("two customers, twelve employees, and four of those employees are also admin")
    void seedsTheRolesOfAdr0006() {
        assertThat(countOfRole("CUSTOMER")).isEqualTo(2);
        assertThat(countOfRole("EMPLOYEE")).isEqualTo(12);
        assertThat(countOfRole("ADMIN")).isEqualTo(4);

        assertThat(jdbc.queryForList(
                        "SELECT account_id FROM account_role WHERE role = 'ADMIN' "
                                + "AND account_id NOT IN (SELECT account_id FROM account_role WHERE role = 'EMPLOYEE')",
                        UUID.class))
                .describedAs("admin is a sub-flag of employee, never a role on its own")
                .isEmpty();
    }

    /**
     * The rule no constraint in this database can enforce: the two halves of it live in
     * {@code customer} and {@code employee}, in a different database, with no foreign key either way.
     * It is the data's job, so it is asserted rather than assumed.
     *
     * <p>The trap it guards is real and already in the seed: {@code joel.sandoval@ukun.com} and
     * {@code joel.sandoval@email.com} are the same person with two accounts, two ids and two roles,
     * which is what ADR-0006 requires — not one account carrying both.
     */
    @Test
    @DisplayName("no account is a customer and an employee at once")
    void keepsCustomerAndEmployeeMutuallyExclusive() {
        assertThat(jdbc.queryForList(
                        "SELECT account_id FROM account_role WHERE role = 'CUSTOMER' "
                                + "INTERSECT SELECT account_id FROM account_role WHERE role = 'EMPLOYEE'",
                        UUID.class))
                .isEmpty();
    }

    /**
     * The end of the chain, and the acceptance criterion of the slice: a seeded row, the login form,
     * the encoder and the roles together produce an authenticated session. The three assertions above
     * would all pass against a database whose accounts nobody can actually sign in to — a hash of the
     * wrong password verifies nothing, but so does a correct hash on a disabled account.
     *
     * <p>Three different emails, so the rate limiter (a bucket per IP and email) never enters the
     * picture.
     */
    @Test
    @DisplayName("one account of each role signs in with the documented password")
    void signsInWithOneAccountOfEachRole() {
        FormLogin login = new FormLogin(http);

        assertThat(login.logIn(CUSTOMER, DEMO_PASSWORD).succeeded()).isTrue();
        assertThat(login.logIn(EMPLOYEE, DEMO_PASSWORD).succeeded()).isTrue();
        assertThat(login.logIn(ADMIN, DEMO_PASSWORD).succeeded()).isTrue();
    }

    private Integer countOf(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private Integer countOfRole(String role) {
        return jdbc.queryForObject("SELECT count(*) FROM account_role WHERE role = ?", Integer.class, role);
    }
}
