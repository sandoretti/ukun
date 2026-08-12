package dev.sandoretti.ukun.api.inventory;

import dev.sandoretti.ukun.api.AbstractIntegrationTest;
import dev.sandoretti.ukun.api.catalog.ProductMother;
import dev.sandoretti.ukun.api.catalog.domain.ProductType;
import dev.sandoretti.ukun.api.stores.StoreMother;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The shop the three stock suites share: two stores, five products, and an employee in each store
 * (RF-09).
 *
 * <p>One fixture and one application context for the three of them, because a {@code @SpringBootTest}
 * per class is a context per class and the contexts of this suite are cached rather than closed —
 * which is how a class that had nothing to do with the change ends up failing with
 * {@code FATAL: sorry, too many clients already}.
 *
 * <p><strong>Two stores, and both of them stocked, is the load-bearing part.</strong> A fixture with
 * one store would pass every listing assertion here even if the queries ignored {@code store_id}
 * altogether, and the rule those queries exist to enforce — an employee sees their own store and
 * nobody else's — would be untested. Barcelona holds the coffee table that Alcalá does not, so a query
 * that crossed stores would show it in the wrong half of the screen.
 *
 * <p>Real tokens rather than an injected authentication, for the reason the cart and profile suites
 * give: resolving {@code sub} against {@code employee.account_id} is the step under test, and mocking
 * the authentication skips it.
 */
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.liquibase.contexts=prod", "spring.jpa.properties.hibernate.generate_statistics=true"})
abstract class StockIntegrationTest extends AbstractIntegrationTest {

    protected static final String ALCALA = "Ukun Alcalá de Henares";
    protected static final String BARCELONA = "Ukun Barcelona";

    /** Works in Alcalá. Every request that is meant to succeed is hers. */
    protected static final UUID ANA = UUID.fromString("8f2c1e40-0000-4000-8000-0000000000a1");

    /** Works in Barcelona. He is how "someone else's store" is spelled in these tests. */
    protected static final UUID BRUNO = UUID.fromString("8f2c1e40-0000-4000-8000-0000000000b1");

    /** A token with a valid signature and no employee row behind it at all. */
    protected static final UUID GHOST = UUID.fromString("8f2c1e40-0000-4000-8000-0000000000f1");

    /** Seven units in Alcalá. */
    protected static final String STOCKED_SOFA = "SOFA-CUERO";

    /** A line in Alcalá sitting at zero — stocked, and out of stock. Not the same as having no line. */
    protected static final String STOCKED_AT_ZERO = "LITERA-INFANTIL";

    /** Stocked in Barcelona only: in Alcalá it is a candidate to be added. */
    protected static final String ONLY_IN_BARCELONA = "MESA-CAFE";

    /** No line anywhere. The plainest {@code NOT_STOCKED} case, and the one the writes use. */
    protected static final String NEVER_STOCKED = "LAMPARA-PIE";

    /** Discontinued, and stocked in Alcalá all the same — discontinuing empties no warehouse (ADR-0013). */
    protected static final String DISCONTINUED = "SILLA-RETIRADA";

    @DynamicPropertySource
    static void stockEnvironment(DynamicPropertyRegistry registry) {
        useDatabase(registry, STOCK_DATABASE);
        useJwks(registry);
    }

    @Autowired
    protected RestTestClient restTestClient;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seedTheStores() {
        inATransaction(() -> List.of(StoreMother.alcalaStore(), StoreMother.barcelonaStore())
                .forEach(store -> {
                    entityManager.persist(store.getAddress());
                    entityManager.persist(store);
                }));

        inATransaction(() -> {
            ProductType furniture = ProductMother.type("Muebles");
            entityManager.persist(furniture);

            List.of(
                            ProductMother.leatherSofa(furniture),
                            ProductMother.bunkBed(furniture),
                            ProductMother.coffeeTable(furniture),
                            ProductMother.floorLamp(furniture),
                            ProductMother.discontinuedChair(furniture))
                    .forEach(entityManager::persist);
        });

        long alcala = storeIdOf(ALCALA);
        long barcelona = storeIdOf(BARCELONA);

        inATransaction(() -> {
            entityManager.persist(StoreStockMother.stock(productIdOf(STOCKED_SOFA), alcala, 7));
            entityManager.persist(StoreStockMother.outOfStock(productIdOf(STOCKED_AT_ZERO), alcala));
            entityManager.persist(StoreStockMother.stock(productIdOf(DISCONTINUED), alcala, 4));
            entityManager.persist(StoreStockMother.stock(productIdOf(ONLY_IN_BARCELONA), barcelona, 5));
        });

        EmployeeRows.insert(jdbc, ANA, "Ana", alcala, false);
        EmployeeRows.insert(jdbc, BRUNO, "Bruno", barcelona, false);
    }

    /**
     * One statement, and {@code address} in it: a store's address is pinned by
     * {@code ON DELETE RESTRICT}, so truncating the two separately leaves whichever went first stuck.
     */
    @AfterEach
    void clearTheStores() {
        jdbc.execute(
                """
                TRUNCATE TABLE store_stock, employee, product, product_type, store, address
                RESTART IDENTITY CASCADE
                """);
    }

    protected long alcalaId() {
        return storeIdOf(ALCALA);
    }

    protected String stockOf(long storeId) {
        return "/api/stores/%d/stock".formatted(storeId);
    }

    protected String lineOf(long storeId, long lineId) {
        return "/api/stores/%d/stock/%d".formatted(storeId, lineId);
    }

    /**
     * The SKUs a listing answered, in the order it answered them.
     *
     * <p>Deserialising the page rather than reading {@code $.items[*].sku} out of it, because half of
     * what these tests assert is about <em>order</em> and about a row being absent, and a JSON path
     * that matches nothing is indistinguishable from one that matched an empty list.
     */
    protected List<String> skusOf(String uri, UUID employee) {
        StockPage page = restTestClient
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(employee)))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(StockPage.class)
                .returnResult()
                .getResponseBody();

        return page.items().stream().map(StockPage.Line::sku).toList();
    }

    /** The identifier of a line, by the SKU of the product it points at. */
    protected long lineIdOf(String sku, long storeId) {
        return jdbc.queryForObject(
                """
                SELECT ss.id
                  FROM store_stock ss
                  JOIN product p ON p.id = ss.product_id
                 WHERE p.sku = ? AND ss.store_id = ?
                """,
                Long.class,
                sku,
                storeId);
    }

    /**
     * A page of stock lines as the client reads it — declared here rather than reusing
     * {@code PageResponse<StockLineResponse>} so that a field the production types stop publishing
     * shows up as a missing value in a test, not as a type that changed underneath it.
     */
    protected record StockPage(List<Line> items, int page, int size, long totalElements, int totalPages) {

        protected record Line(Long lineId, Long productId, String sku, String productName, Integer quantity, Long version) {}
    }
}
