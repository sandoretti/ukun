package dev.sandoretti.ukun.api;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The single Postgres container of the whole suite, reused across every {@code *IT} that needs a
 * database — the pattern {@code docs/13-testing.md} §1 requires, and the reason issue #11 exists:
 * before this class, each of the three integration tests started a container of its own.
 *
 * <p>It is started from a static initialiser rather than with {@code @Testcontainers} and
 * {@code @Container}. Those two hand the lifecycle to JUnit, which stops the container after the
 * last test of <em>each</em> class — the very thing that turns a shared container into one per
 * class. Ryuk removes it when the JVM exits.
 *
 * <h2>Why three databases and not one</h2>
 *
 * The integration tests need mutually exclusive states in the same instance. {@link SchemaBaselineIT}
 * runs under {@code contexts=prod} and asserts the schema changesets and no demo row anywhere;
 * {@link DemoDataIT} runs under {@code demo} and asserts the seeded catalogue. Sharing one database
 * would make the order in which the classes happen to run decide which of the two fails — and
 * {@code demoDataNeverReachesAProductionContext()} would stop meaning anything at all. So the
 * container holds one database per state, created on first use.
 *
 * <p>{@link #CATALOG_DATABASE} is the third, and it exists for a different reason than the other two:
 * a test that exercises a domain query <em>writes</em> its own rows with an object mother, which is
 * what the {@code testing} skill asks for so the case under test is built explicitly instead of
 * being fished out of the seed data. Those rows must not be visible to
 * {@code demoDataNeverReachesAProductionContext()}, and the seed data must not be visible to a test
 * that counts its own products. It is migrated under {@code prod}: schema, no data.
 *
 * <p>Consequence worth stating: this is why the datasource is wired with
 * {@code @DynamicPropertySource} instead of the {@code @ServiceConnection} that {@code docs/13} §1
 * describes. {@code @ServiceConnection} points the context at the container's own database and
 * offers no way to choose another one — a shared container with more than one database state is
 * exactly the case it does not cover. The dynamic properties are added ahead of every other
 * source, so they win over the {@code ${SPRING_DATASOURCE_*}} placeholders of
 * {@code application.yaml} just as the annotation used to.
 *
 * <p>A subclass declares which database it talks to, and nothing else:
 *
 * <pre>{@code
 * @DynamicPropertySource
 * static void database(DynamicPropertyRegistry registry) {
 *     useDatabase(registry, SCHEMA_ONLY_DATABASE);
 * }
 * }</pre>
 */
public abstract class AbstractIntegrationTest {

    /** Schema only: what the {@code prod} Liquibase context produces. */
    protected static final String SCHEMA_ONLY_DATABASE = "ukun_app_schema";

    /** Schema plus the demo catalogue: what the {@code demo} context produces. */
    protected static final String SEEDED_DATABASE = "ukun_app_demo";

    /** Schema only, for the catalogue tests that seed their own rows with an object mother. */
    protected static final String CATALOG_DATABASE = "ukun_app_catalog";

    /**
     * Schema only, the same idea as {@link #CATALOG_DATABASE} for the store network and its stock.
     * Separate from it because the catalogue's truncation cascades through {@code product} into
     * {@code store_stock}: two suites clearing each other's tables is a failure that only appears when
     * somebody reorders a test class.
     */
    protected static final String STORES_DATABASE = "ukun_app_stores";

    /**
     * Schema only, for the signup saga. It gets one of its own because {@code SignupIT} is the first
     * suite that writes {@code customer}, and a profile pins the {@code address} and {@code card} rows
     * it points at through {@code ON DELETE RESTRICT} — so a suite that truncates those tables would
     * fail against leftovers of this one, which is exactly the coupling {@link #STORES_DATABASE}
     * documents.
     */
    protected static final String SIGNUP_DATABASE = "ukun_app_signup";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    /**
     * Creates {@code database} if this is the first class to ask for it and points the context's
     * datasource at it. The {@code test} and {@code integrationTest} tasks run test classes sequentially, so there is no race
     * to guard here.
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

    protected static PostgreSQLContainer postgres() {
        return POSTGRES;
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
