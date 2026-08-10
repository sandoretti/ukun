package dev.sandoretti.ukun.api.catalog;

import dev.sandoretti.ukun.api.AbstractIntegrationTest;
import dev.sandoretti.ukun.api.catalog.domain.ProductType;
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
 * The catalogue fixture the four public read endpoints of v0.3 share: a running application, a real
 * Postgres, and the five products of {@link ProductMother} seeded before every test.
 *
 * <p>Testcontainers rather than H2, per the {@code testing} skill, and here that is not ceremony: a
 * good half of these tests are about {@code unaccent()}, a Postgres extension H2 does not have.
 *
 * <p>The rows are written into {@link #CATALOG_DATABASE} with an object mother, so each defect under
 * test is built explicitly instead of being fished out of the demo catalogue — and so nothing written
 * here is visible to the tests that count the seed data.
 *
 * <h2>Why the Spring annotations live on this class and not on each test</h2>
 *
 * The context cache key includes the {@code @DynamicPropertySource} methods themselves, so four
 * subclasses each declaring their own would get four application contexts and four Liquibase runs for
 * one identical configuration. Declared once here and inherited ({@code @SpringBootTest} and
 * {@code @AutoConfigureRestTestClient} are both {@code @Inherited}), the four share a single context.
 *
 * <p>{@code springdoc.api-docs.enabled} is turned on for the tests that diff the generated document
 * against the frozen contract, rather than activating the {@code dev} profile: the profile would also
 * switch Liquibase to the {@code demo} context and seed a hundred products into the database these
 * tests count rows in.
 */
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.liquibase.contexts=prod",
            "spring.jpa.properties.hibernate.generate_statistics=true",
            "springdoc.api-docs.enabled=true"
        })
abstract class CatalogIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, CATALOG_DATABASE);
    }

    @Autowired
    protected RestTestClient restTestClient;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    /** Persisted in a transaction of its own that commits before any request is made. */
    @BeforeEach
    void seedTheCatalogue() {
        ProductType sofas = ProductMother.type("Sofás");
        ProductType beds = ProductMother.type("Camas");
        ProductType tables = ProductMother.type("Mesas");

        inATransaction(() -> {
            List.of(sofas, beds, tables).forEach(entityManager::persist);
            List.of(
                            ProductMother.leatherSofa(sofas),
                            ProductMother.bunkBed(beds),
                            ProductMother.coffeeTable(tables),
                            ProductMother.floorLamp(tables),
                            ProductMother.discontinuedChair(tables))
                    .forEach(entityManager::persist);
        });
    }

    /** Ordered truncation rather than {@code @DirtiesContext}, per the `testing` skill. */
    @AfterEach
    void clearTheCatalogue() {
        jdbc.execute("TRUNCATE TABLE product, product_type RESTART IDENTITY CASCADE");
    }

    protected long typeIdOf(String name) {
        return jdbc.queryForObject("SELECT id FROM product_type WHERE name = ?", Long.class, name);
    }

    protected long productIdOf(String sku) {
        return jdbc.queryForObject("SELECT id FROM product WHERE sku = ?", Long.class, sku);
    }
}
