package dev.sandoretti.ukun.api.catalog;

import dev.sandoretti.ukun.api.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The fixture the back-office suites of RF-10 and RF-13 share: a running application, a real Postgres,
 * the same five products {@link ProductMother} seeds for the public catalogue, and an administrator's
 * token.
 *
 * <p>A fixture of its own rather than an extension of {@code CatalogIntegrationTest}, for the reason
 * {@link #BACKOFFICE_DATABASE} gives: these suites write and delete the rows that one counts.
 *
 * <p>The Spring annotations live here and not on each test so the three subclasses share one
 * application context — the context cache key includes the {@code @DynamicPropertySource} methods
 * themselves, so declaring them per class would mean three contexts and three Liquibase runs for one
 * identical configuration.
 *
 * <p>Real tokens rather than {@code SecurityMockMvcRequestPostProcessors.jwt()}: what these endpoints
 * are protected by is a role claim inside a signed token, and a post-processor that installs an
 * authority skips the very layer {@code /api/admin/**} depends on (ADR-0020).
 */
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.liquibase.contexts=prod", "spring.jpa.properties.hibernate.generate_statistics=true"})
abstract class BackOfficeIntegrationTest extends AbstractIntegrationTest {

    protected static final UUID ADMIN_ACCOUNT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    protected static final String SOFAS = "Sofás";
    protected static final String BEDS = "Camas";
    protected static final String TABLES = "Mesas";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, BACKOFFICE_DATABASE);
        useJwks(registry);
    }

    @Autowired
    protected RestTestClient restTestClient;

    @Autowired
    private EntityManager entityManager;

    /**
     * The same catalogue the public suite reads, persisted in a transaction of its own that commits
     * before any request is made. It comes from the mother rather than from a copy of that fixture's
     * seeding, so the cases it encodes — the price tie, the accents, the discontinued product — are the
     * same rows on both sides.
     */
    @BeforeEach
    void seedTheCatalogue() {
        inATransaction(() -> ProductMother.demoCatalogue().forEach(entityManager::persist));
    }

    /**
     * Ordered truncation rather than {@code @DirtiesContext}, per the {@code testing} skill. It names
     * more tables than the public catalogue's fixture because the delete tests build the references that
     * block a deletion, and {@code CASCADE} alone would leave the orders and the customers behind — the
     * halves that do not point at {@code product}.
     */
    @AfterEach
    void clearTheCatalogue() {
        jdbc.execute(
                """
                TRUNCATE TABLE order_line, customer_order, cart_item, store_stock,
                               product, product_type, customer, employee, store, address, card
                RESTART IDENTITY CASCADE
                """);
    }

    protected long typeIdOf(String name) {
        return jdbc.queryForObject("SELECT id FROM product_type WHERE name = ?", Long.class, name);
    }

    protected String admin() {
        return bearer(adminToken(ADMIN_ACCOUNT));
    }
}
