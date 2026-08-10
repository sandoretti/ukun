package dev.sandoretti.ukun.api.stores;

import dev.sandoretti.ukun.api.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The store network the two public reads of v0.3 that involve a store share: a running application, a
 * real Postgres, and the four stores of {@link StoreMother} seeded before every test.
 *
 * <p><strong>Why the availability tests of {@code inventory} extend this too.</strong> Availability is
 * the stock of a product <em>in a store</em>: the endpoint resolves the store first and answers
 * {@code 404} when it does not exist, so there is no way to set that test up without the same fixture.
 * Sharing the base is also what keeps the two suites on one application context — the cache key
 * includes the {@code @DynamicPropertySource} methods themselves, so a second base declaring an
 * identical one would mean a second context and a second Liquibase run for the same configuration.
 *
 * <p>A database of its own ({@link #STORES_DATABASE}) rather than the catalogue's: both are migrated
 * under {@code prod} and both seed their own rows, but the catalogue's truncation cascades through
 * {@code product} into {@code store_stock}, and two suites clearing each other's tables is the kind of
 * coupling that only shows up as a failure when someone reorders a test class.
 *
 * <p>Testcontainers rather than H2, per the {@code testing} skill, and here it is not ceremony: the
 * search tests exercise {@code unaccent()}, a Postgres extension H2 does not have.
 *
 * <p>{@code springdoc.api-docs.enabled} is turned on for the tests that diff the generated document
 * against the frozen contract, rather than activating the {@code dev} profile: the profile would also
 * switch Liquibase to the {@code demo} context and seed a hundred products into a database these tests
 * count rows in.
 */
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.liquibase.contexts=prod",
            "spring.jpa.properties.hibernate.generate_statistics=true",
            "springdoc.api-docs.enabled=true"
        })
public abstract class StoresIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, STORES_DATABASE);
    }

    @Autowired
    protected RestTestClient restTestClient;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected EntityManager entityManager;

    /**
     * Committed before any request is made: the endpoint is served on another thread, so rows left
     * inside an open test transaction would be invisible to it.
     */
    @BeforeEach
    void seedTheStoreNetwork() {
        inATransaction(() -> List.of(
                        StoreMother.onlineStore(),
                        StoreMother.alcalaStore(),
                        StoreMother.torrejonStore(),
                        StoreMother.barcelonaStore())
                .forEach(store -> {
                    entityManager.persist(store.getAddress());
                    entityManager.persist(store);
                }));
    }

    /** Ordered truncation rather than {@code @DirtiesContext}, per the {@code testing} skill. */
    @AfterEach
    void clearTheStoreNetwork() {
        jdbc.execute("TRUNCATE TABLE store, address RESTART IDENTITY CASCADE");
    }

    protected long storeIdOf(String name) {
        return jdbc.queryForObject("SELECT id FROM store WHERE name = ?", Long.class, name);
    }
}
