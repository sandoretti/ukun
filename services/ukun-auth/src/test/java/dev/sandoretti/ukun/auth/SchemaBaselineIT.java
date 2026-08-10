package dev.sandoretti.ukun.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The ukun_auth baseline applied to an empty database, which is the fourth rule of the
 * {@code liquibase} skill: a changeset is only proven against a virgin database, never against a
 * local one that has already evolved and can hide an assumed previous state.
 *
 * <p>It asserts the schema in two registers. Metadata — the tables, indexes and delete rules are
 * exactly the ones {@code docs/10-modelo-de-datos.md} §8 and {@code docs/08-identidad-y-seguridad.md}
 * §2 decided, no more and no fewer. Behaviour — the invariants that only count if the database
 * itself enforces them: case-insensitive uniqueness of the login email, the closed list of roles,
 * and an account that cannot be born enabled by omission.
 *
 * <p>The context is pinned to {@code prod} so this class keeps measuring the baseline and only the
 * baseline, and it takes the schema-only database so no demo changeset ever runs against it —
 * whichever order the classes of this suite execute in.
 */
@SpringBootTest(properties = "spring.liquibase.contexts=prod")
class SchemaBaselineIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, SCHEMA_ONLY_DATABASE);
    }

    /**
     * {@code docs/10-modelo-de-datos.md} §8: three tables of this service's own plus the three the
     * Authorization Server brings, and the baseline creates all six at once.
     */
    private static final List<String> EXPECTED_TABLES = List.of(
            "account",
            "account_role",
            "registration_request",
            "oauth2_registered_client",
            "oauth2_authorization",
            "oauth2_authorization_consent");

    /**
     * The two unique constraints and the single explicit index. Primary keys are excluded — they are
     * not a decision this list records.
     *
     * <p>{@code containsExactlyInAnyOrder} rather than {@code contains} is the point: the baseline
     * writes down what it deliberately leaves out (anything on {@code account.enabled} for the
     * reconciliation job, anything on the {@code oauth2_*} tables), and an index that creeps in
     * "just in case" fails here.
     */
    private static final List<String> EXPECTED_INDEXES =
            List.of("ux_account_email", "ux_oauth2_registered_client_id", "ix_registration_request_account");

    /** Both children belong to the account and go with it — {@code docs/10} §6. */
    private static final Map<String, String> EXPECTED_DELETE_RULES = Map.of(
            "fk_account_role_account", "CASCADE",
            "fk_registration_request_account", "CASCADE");

    /** {@code docs/10} §1 (every mandatory text is non-blank) and ADR-0006 (the role list). */
    private static final Map<String, Long> EXPECTED_CHECKS_PER_TABLE = Map.of("account", 2L, "account_role", 1L);

    private static final String CHANGE_LOG = "classpath:db/changelog/db.changelog-master.yaml";

    /** The eleven changesets of {@code baseline/001-esquema-inicial.yaml}. */
    private static final int EXPECTED_CHANGESETS = 11;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("the baseline creates the six tables of docs/10 §8 and nothing else")
    void createsEveryTableOfTheDataModel() {
        List<String> tables = jdbc.queryForList(
                """
                SELECT table_name FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_type = 'BASE TABLE'
                   AND table_name NOT LIKE 'databasechangelog%'
                """,
                String.class);

        assertThat(tables).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
    }

    /**
     * ADR-0004 in the form the database can be asked about it: no profile column ever lands here. If
     * this fails, somebody moved a field across the service boundary without reopening the ADR — and
     * that turns every profile edit into a distributed write for a datum that is not a credential.
     */
    @Test
    @DisplayName("no profile column leaks into account: name and last_name stay in ukun_app")
    void accountHoldsCredentialsOnly() {
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'account'",
                String.class);

        assertThat(columns)
                .containsExactlyInAnyOrder("id", "email", "password_hash", "enabled", "created_at", "updated_at");
    }

    @Test
    @DisplayName("the indexes are exactly the ones the baseline justifies, with their agreed names")
    void createsExactlyTheJustifiedIndexes() {
        List<String> indexes = jdbc.queryForList(
                """
                SELECT indexname FROM pg_indexes
                 WHERE schemaname = 'public'
                   AND indexname NOT LIKE 'pk_%'
                   AND tablename NOT LIKE 'databasechangelog%'
                """,
                String.class);

        assertThat(indexes).containsExactlyInAnyOrderElementsOf(EXPECTED_INDEXES);
    }

    /**
     * The one assertion this file exists for above the others. Liquibase's OSS {@code createTable}
     * accepts a {@code checkConstraint} attribute and then discards it: the changeset reports success
     * and the constraint is absent. So every CHECK in the baseline is written as raw SQL, and this
     * counts them back out of the database.
     */
    @Test
    @DisplayName("every CHECK of the baseline reached the database")
    void everyCheckConstraintExists() {
        Map<String, Long> checksPerTable = jdbc
                .queryForList(
                        """
                        SELECT tc.table_name
                          FROM information_schema.table_constraints tc
                         WHERE tc.constraint_type = 'CHECK'
                           AND tc.table_schema = 'public'
                           AND tc.constraint_name LIKE 'ck_%'
                        """)
                .stream()
                .collect(Collectors.groupingBy(row -> (String) row.get("table_name"), Collectors.counting()));

        assertThat(checksPerTable).containsExactlyInAnyOrderEntriesOf(EXPECTED_CHECKS_PER_TABLE);
    }

    @Test
    @DisplayName("every foreign key carries the ON DELETE policy docs/10 §6 decided for it")
    void everyForeignKeyDeclaresItsDeletePolicy() {
        Map<String, String> rules = jdbc
                .queryForList(
                        """
                        SELECT tc.constraint_name, rc.delete_rule
                          FROM information_schema.table_constraints tc
                          JOIN information_schema.referential_constraints rc
                            ON rc.constraint_name = tc.constraint_name
                           AND rc.constraint_schema = tc.table_schema
                         WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public'
                        """)
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row.get("constraint_name"), row -> (String) row.get("delete_rule")));

        assertThat(rules).containsExactlyInAnyOrderEntriesOf(EXPECTED_DELETE_RULES);
    }

    /**
     * CITEXT is chosen so that the guarantee lives in the type rather than in remembering
     * {@code lower()} at every comparison — Hibernate turns a derived {@code findByEmail} into
     * {@code where email = ?} and would skip it silently. This is that guarantee, asked of the
     * database directly.
     */
    @Test
    @DisplayName("the same email in a different case is the same account, and the database says so")
    void emailUniquenessIsCaseInsensitive() {
        insertAccount("person@ukun.dev");

        assertThatThrownBy(() -> insertAccount("Person@Ukun.DEV"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_account_email");
    }

    /**
     * ADR-0006 says a business role is one of three. Leaving that to the Java enum alone is the
     * legacy's mistake in {@code docs/10} §1 — an entity promising more than the database enforces.
     */
    @Test
    @DisplayName("a role outside the closed list of ADR-0006 is rejected by the database")
    void onlyTheThreeBusinessRolesAreAccepted() {
        UUID accountId = insertAccount("roles@ukun.dev");

        assertThatCode(() -> jdbc.update("INSERT INTO account_role (account_id, role) VALUES (?, 'CUSTOMER')", accountId))
                .doesNotThrowAnyException();

        assertThatThrownBy(
                        () -> jdbc.update("INSERT INTO account_role (account_id, role) VALUES (?, 'SUPERUSER')", accountId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_account_role_role");
    }

    /**
     * Rule 4 of the {@code spring-auth} skill: an account is reserved first and activated second, so
     * that a signup saga interrupted halfway leaves inert rubbish rather than a live account with no
     * customer behind it. Making {@code false} the column default is what stops that from depending
     * on every caller remembering.
     */
    @Test
    @DisplayName("an account inserted without saying so is born disabled")
    void anAccountIsNeverEnabledByOmission() {
        UUID accountId = insertAccount("reserved@ukun.dev");

        assertThat(jdbc.queryForObject("SELECT enabled FROM account WHERE id = ?", Boolean.class, accountId))
                .isFalse();
    }

    /**
     * The counterpart of {@link DemoAccountsIT}: under the {@code prod} context the changelog creates
     * the schema and stops there. Without an explicit {@code spring.liquibase.contexts} Liquibase
     * runs every changeset regardless of its context, and the demo ones seed accounts whose password
     * is written down in {@code docs/06-entorno-local-tilt.md}.
     */
    @Test
    @DisplayName("the demo accounts never reach a production context")
    void demoDataNeverReachesAProductionContext() {
        assertThat(countOf("account")).isZero();
        assertThat(countOf("account_role")).isZero();
        assertThat(countOf("oauth2_registered_client")).isZero();
    }

    /**
     * The second run goes through a datasource of its own. {@link SpringLiquibase} closes the
     * datasource it migrated unless told otherwise, so reusing the context's pool here would leave
     * every later test in this class without a connection. It also has to be told the context: a
     * {@code SpringLiquibase} built by hand defaults to running everything.
     */
    @Test
    @DisplayName("re-running the changelog against an up-to-date database applies nothing")
    void applyingTheChangelogTwiceIsANoOp() throws Exception {
        Integer before = jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Integer.class);

        SpringLiquibase secondRun = new SpringLiquibase();
        secondRun.setDataSource(
                new SimpleDriverDataSource(new Driver(), jdbcUrlOf(SCHEMA_ONLY_DATABASE), username(), password()));
        secondRun.setChangeLog(CHANGE_LOG);
        secondRun.setContexts("prod");
        secondRun.setResourceLoader(new DefaultResourceLoader());
        secondRun.afterPropertiesSet();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Integer.class))
                .isEqualTo(before)
                .isEqualTo(EXPECTED_CHANGESETS);
    }

    private UUID insertAccount(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO account (id, email, password_hash) VALUES (?, ?, ?)", id, email, "{bcrypt}$2a$10$fake");
        return id;
    }

    private long countOf(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    /** Ordered truncation rather than {@code @DirtiesContext}, per the `testing` skill. */
    @AfterEach
    void clearTheRowsThisTestWrote() {
        jdbc.execute("TRUNCATE TABLE " + String.join(", ", EXPECTED_TABLES) + " RESTART IDENTITY CASCADE");
    }
}
