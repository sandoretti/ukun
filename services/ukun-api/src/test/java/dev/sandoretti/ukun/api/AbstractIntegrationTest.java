package dev.sandoretti.ukun.api;

import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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

    /**
     * Schema only, for the profile of {@code /api/me}. Same reasoning as {@link #SIGNUP_DATABASE} and
     * for the same three tables: these suites write and edit {@code customer} with its {@code address}
     * and {@code card}, and sharing a database with the saga's would mean each one truncating rows the
     * other still points at.
     */
    protected static final String PROFILE_DATABASE = "ukun_app_profile";

    /**
     * Schema only, for the cart. It touches more tables than any suite before it — {@code cart_item}
     * over {@code customer}, {@code product} and {@code store_stock} at once — so it is the one that
     * would collide with everybody: the catalogue's truncation cascades into its stock, and the profile
     * suites pin the {@code address} and {@code card} rows its customers point at.
     */
    protected static final String CART_DATABASE = "ukun_app_cart";

    /**
     * Schema only, for checkout and orders. It touches everything the cart does and then writes on top
     * of it — {@code customer_order}, {@code order_line}, {@code checkout_attempt} and
     * {@code idempotency_key} — and it is the one suite that <em>decrements</em> stock, so sharing a
     * database with the cart's would leave each of them buying rows the other counted.
     */
    protected static final String ORDERS_DATABASE = "ukun_app_orders";

    /**
     * Schema only, for the employee's stock management (RF-09). It seeds {@code employee} rows, which no
     * other suite does, and it writes {@code store_stock} for stores it owns — so it would fight the
     * catalogue's fixture for products and the orders' one for quantities if they shared a database.
     */
    protected static final String STOCK_DATABASE = "ukun_app_stock";

    /**
     * Schema only, for the back-office of RF-10 and RF-13. It is the widest fixture of the catalogue
     * side: it edits and deletes {@code product} and {@code product_type} — which the public catalogue
     * suite reads and counts — and it seeds a stock line, a cart line and an order line to prove that a
     * referenced product cannot be deleted. Sharing a database with any of those suites would mean each
     * one truncating rows the other had just built its assertion on.
     */
    protected static final String BACKOFFICE_DATABASE = "ukun_app_backoffice";

    /**
     * Connections per application context, against Hikari's default of ten.
     *
     * <p>The contexts of this suite are <strong>cached, not closed</strong>: every one of them keeps its
     * pool open until the JVM exits, so the connections add up across classes while the container's
     * {@code max_connections} stays at Postgres' default of 100. At ten apiece the ceiling is a dozen
     * contexts, and the suite reached it the day the cart added one — as
     * {@code FATAL: sorry, too many clients already} in a class that had nothing to do with the change
     * and passed on its own. Four is comfortable: the tests are sequential, so a context serves one
     * request at a time plus the fixture's own transaction.
     *
     * <p>It is set here rather than in each {@code @SpringBootTest} because this is the method every
     * fixture already calls to get a datasource, and a limit that has to be remembered per class is one
     * that will be missing from the next one.
     *
     * <p><strong>One context overrides it</strong>, and only one: {@code OrdersIntegrationTest}, whose
     * concurrency suite has several checkouts in flight at once and every one of them holding its
     * connection while it waits on a row lock. "A context serves one request at a time" is what makes
     * four enough, and that is exactly what that suite exists not to do. Its own javadoc has the
     * arithmetic.
     */
    private static final int TEST_POOL_SIZE = 4;

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    /**
     * The single JWKS of the whole suite, next to the single container and for the same reasons.
     *
     * <p>It used to be three: {@code ResourceServerIT}, the profile fixture and the cart each started one
     * of their own, and each answered the lifecycle question differently — an {@code @AfterAll} in the
     * first, a shutdown hook in the second, and the second's javadoc explaining why the first is wrong
     * for a server more than one class relies on. Both cannot be right, and the disagreement was only
     * harmless because no two of them shared a server. One instance, closed by the JVM, removes the
     * question: {@code @AfterAll} would stop it after whichever class finished first while the contexts
     * that point at it stay cached and alive.
     */
    private static final JwksTestServer AUTH = JwksTestServer.start();

    static {
        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(AbstractIntegrationTest::stopAuth));
    }

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * Declared once here rather than in each fixture, where it was repeated seven times. Every suite
     * needs it for the same two jobs — the ordered truncation of its own tables, and reading back a
     * column no endpoint exposes — and neither is worth an {@code @Autowired} field per class.
     */
    @Autowired
    protected JdbcTemplate jdbc;

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
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> TEST_POOL_SIZE);
    }

    /**
     * Points the context's resource server at the suite's JWKS, so its tokens are validated the way
     * production validates them — signature, issuer and audience, over HTTP.
     *
     * <p>Called from the fixture's own {@code @DynamicPropertySource} rather than declared here as one:
     * the context cache key is built from those methods, so a single inherited one would hand two
     * fixtures the same key and, with it, each other's database.
     */
    protected static void useJwks(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", AUTH::jwkSetUri);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> JwksTestServer.ISSUER);
    }

    /** The suite's token mint, for the tests that need a claim set nobody else asks for. */
    protected static JwksTestServer auth() {
        return AUTH;
    }

    /**
     * The identifier of a row a fixture seeded, found by the business key the object mother named it
     * with — {@code sku} for a product, {@code name} for a store.
     *
     * <p>Both were written out three times, in the catalogue, the stores, the profile, the availability
     * and the cart suites, always as the same single statement. They live here, next to the databases
     * each of those suites uses, because the thing they have in common is not the domain: it is that
     * every fixture inserts rows whose keys are generated and then needs to name one of them in a URL.
     * The mothers stay pure builders, which is what makes them usable outside a Spring context.
     *
     * <p>Not a generic {@code idOf(table, column, value)}: the two tables that need it are the two that
     * have a business key at all, and a helper that took a table name would be an invitation to build
     * SQL from strings in a test.
     */
    protected long productIdOf(String sku) {
        return jdbc.queryForObject("SELECT id FROM product WHERE sku = ?", Long.class, sku);
    }

    /** @see #productIdOf(String) */
    protected long storeIdOf(String name) {
        return jdbc.queryForObject("SELECT id FROM store WHERE name = ?", Long.class, name);
    }

    protected static String customerToken(UUID accountId, String email) {
        return AUTH.validToken(accountId, email, List.of("CUSTOMER"));
    }

    /**
     * The same token for a suite that does not assert on the email — most of them, since the claim
     * only matters to {@code /api/me}. Written privately in the cart suite first and then again in the
     * checkout one, which is the second use that earns it a place here.
     */
    protected static String customerToken(UUID accountId) {
        return customerToken(accountId, accountId + "@ukun.dev");
    }

    protected static String employeeToken(UUID accountId, String email) {
        return AUTH.validToken(accountId, email, List.of("EMPLOYEE"));
    }

    /** @see #customerToken(UUID) — same reasoning, for the suites that only need the role. */
    protected static String employeeToken(UUID accountId) {
        return employeeToken(accountId, accountId + "@ukun.com");
    }

    /**
     * An administrator, which is an employee carrying a flag and not a rank above one (RF-12, ADR-0006)
     * — so the token holds both roles, exactly as ukun-auth mints it. A token with {@code ADMIN} alone
     * would pass every {@code /api/admin/**} check while failing the stock endpoints, which is not a
     * state any real account can be in.
     */
    protected static String adminToken(UUID accountId) {
        return AUTH.validToken(accountId, accountId + "@ukun.com", List.of("EMPLOYEE", "ADMIN"));
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    private static void stopAuth() {
        try {
            AUTH.close();
        } catch (Exception e) {
            // Nothing to recover on the way out of the JVM; the port goes with the process.
        }
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
     * Runs {@code work} in a transaction of its own that commits before it returns. Every fixture in
     * this suite needs it for the same reason: the endpoint under test is served on another thread, so
     * rows left inside an open test transaction would be invisible to it.
     */
    protected void inATransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    /**
     * A cleared, enabled Hibernate statement counter. Every statement issued after this call and before
     * the assertion belongs to the request under test, which is what makes an <em>exact</em> count
     * meaningful rather than "at most a few".
     *
     * <p>It lives here rather than in each fixture because the defect it guards against is not any one
     * module's: an N+1 is what the {@code @EntityGraph} ladder of {@code docs/03-analisis-y-patrones.md}
     * §2 exists to prevent, and every listing in this service is expected to answer for its statement
     * count. Counting requires {@code hibernate.generate_statistics}, which each fixture turns on with
     * its own {@code @SpringBootTest} properties — a shared context would be a shared cache key.
     */
    protected Statistics freshStatistics() {
        Statistics statistics =
                entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        return statistics;
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
