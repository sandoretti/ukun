package dev.sandoretti.ukun.auth;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The single Postgres container of this service's suite, reused across every {@code *IT} that needs
 * a database — the pattern {@code docs/13-testing.md} §1 requires.
 *
 * <p>It is a deliberate near-copy of the class of the same name in {@code ukun-api}, not a shared
 * one. The two services own separate databases with separate schemas and separate migration
 * histories (skill {@code liquibase}, rule 6), and the set of database states each suite needs is
 * part of that: sharing the base class would mean one of them naming the other's databases. What is
 * genuinely common between the two services — the ArchUnit rules — does live in a shared project,
 * {@code :ukun-test-support}.
 *
 * <p>It is started from a static initialiser rather than with {@code @Testcontainers} and
 * {@code @Container}. Those two hand the lifecycle to JUnit, which stops the container after the
 * last test of <em>each</em> class — the very thing that turns a shared container into one per
 * class. Ryuk removes it when the JVM exits.
 *
 * <h2>Why more than one database</h2>
 *
 * The same reason as in {@code ukun-api}: {@link SchemaBaselineIT} runs under {@code contexts=prod}
 * and asserts the schema and that nothing seeded it, while {@link DemoAccountsIT} needs a database
 * migrated under {@code demo}. Sharing one would let the order in which the classes happen to run
 * decide which of the two fails.
 *
 * <p>That is also why the datasource is wired with {@code @DynamicPropertySource} instead of the
 * {@code @ServiceConnection} that {@code docs/13} §1 describes: {@code @ServiceConnection} points
 * the context at the container's own database and offers no way to choose another one.
 */
public abstract class AbstractIntegrationTest {

    /**
     * The client secrets every {@code @SpringBootTest} of this suite has to supply, because
     * {@code application.yaml} deliberately gives them no default: a service that starts with a
     * well-known client secret is worse than one that refuses to start. They live here so the three
     * fixtures that boot a context state the same value instead of three copies drifting apart.
     */
    protected static final String GATEWAY_SECRET = "test-gateway-secret";

    protected static final String API_SECRET = "test-api-secret";

    /** Schema only: what the {@code prod} Liquibase context produces. */
    protected static final String SCHEMA_ONLY_DATABASE = "ukun_auth_schema";

    /** Schema plus the demo accounts: what the {@code demo} context produces ({@link DemoAccountsIT}). */
    protected static final String SEEDED_DATABASE = "ukun_auth_demo";

    /**
     * Schema only, for the tests that write their own accounts with an object mother. Their rows
     * must not be visible to the assertions of {@link SchemaBaselineIT} that count an empty table.
     */
    protected static final String ACCOUNTS_DATABASE = "ukun_auth_accounts";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    /**
     * Creates {@code database} if this is the first class to ask for it and points the context's
     * datasource at it. The {@code test} and {@code integrationTest} tasks run test classes
     * sequentially, so there is no race to guard here.
     */
    protected static void useDatabase(DynamicPropertyRegistry registry, String database) {
        createDatabaseIfAbsent(database);

        registry.add("spring.datasource.url", () -> jdbcUrlOf(database));
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    protected static String jdbcUrlOf(String database) {
        return "jdbc:postgresql://%s:%d/%s".formatted(POSTGRES.getHost(), POSTGRES.getFirstMappedPort(), database);
    }

    protected static String username() {
        return POSTGRES.getUsername();
    }

    protected static String password() {
        return POSTGRES.getPassword();
    }

    /**
     * {@code CREATE DATABASE} cannot run inside a transaction and Postgres has no {@code IF NOT
     * EXISTS} for it, hence the look-up first. The connection goes to the container's own default
     * database, which no test uses.
     */
    private static void createDatabaseIfAbsent(String database) {
        try (Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()) {

            try (ResultSet existing =
                    statement.executeQuery("SELECT 1 FROM pg_database WHERE datname = '" + database + "'")) {
                if (existing.next()) {
                    return;
                }
            }
            statement.execute("CREATE DATABASE " + database);

        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the test database " + database, e);
        }
    }
}
