package dev.sandoretti.ukun.api.orders;

import dev.sandoretti.ukun.api.AbstractIntegrationTest;
import dev.sandoretti.ukun.api.MutableClock;
import dev.sandoretti.ukun.api.catalog.ProductMother;
import dev.sandoretti.ukun.api.catalog.domain.ProductType;
import dev.sandoretti.ukun.api.customers.CustomerMother;
import dev.sandoretti.ukun.api.inventory.StoreStockMother;
import dev.sandoretti.ukun.api.orders.application.CheckoutAttemptResponse;
import dev.sandoretti.ukun.api.orders.application.OrderResponse;
import dev.sandoretti.ukun.api.stores.StoreMother;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The fixture the checkout suites share: a running application, a real Postgres, real tokens, and a
 * shop with enough stock situations to make each rule of RF-04 visible.
 *
 * <p>The Spring annotations and the {@code @DynamicPropertySource} live here rather than on each
 * subclass so that all five suites share one application context — the cache key includes those
 * methods, so declaring them per class would mean five contexts, five Liquibase runs and five
 * connection pools against a container with a hundred connections.
 *
 * <p><strong>Nothing here builds an order behind the API's back.</strong> The fixture seeds products,
 * stock and customers; carts are filled through {@code PUT /api/cart/items/{id}} and orders are placed
 * through {@code POST /api/orders}, because half of what these suites assert is that the two endpoints
 * agree — an order written with an {@code EntityManager} would prove that JPA works.
 *
 * <p>The shop is the cart suite's, with one addition that matters: {@link #LAST_ONE} has exactly one
 * unit online. Every stock rejection and the whole concurrency suite are built on it.
 */
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = ControllableTime.class,
        properties = {"spring.liquibase.contexts=prod", "spring.jpa.properties.hibernate.generate_statistics=true"})
abstract class OrdersIntegrationTest extends AbstractIntegrationTest {

    protected static final String ATTEMPTS = "/api/checkout/attempts";
    protected static final String ORDERS = "/api/orders";
    protected static final String CART_ITEM = "/api/cart/items/";
    protected static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    protected static final UUID ADA = UUID.fromString("8f2c1e40-0000-4000-8000-0000000000c1");
    protected static final UUID GRACE = UUID.fromString("8f2c1e40-0000-4000-8000-0000000000c2");
    protected static final UUID EMPLOYEE = UUID.fromString("8f2c1e40-0000-4000-8000-0000000000e1");

    /** Seven units online, {@code 399.99} apiece. */
    protected static final String IN_STOCK = "SOFA-CUERO";

    /** One unit online: the last one, and what every rejection in these suites is about. */
    protected static final String LAST_ONE = "LITERA-INFANTIL";

    /** Five units, but in Barcelona — online it has no row at all (RF-04). */
    protected static final String ONLY_IN_BARCELONA = "MESA-CAFE";

    /** Discontinued and stocked online: discontinuing empties no warehouse (ADR-0013). */
    protected static final String DISCONTINUED = "SILLA-RETIRADA";

    /**
     * Buyers contending for the same unit in {@code CheckoutConcurrencyIT}, and therefore the number of
     * connections this context has to be able to hand out at once.
     *
     * <p>Eight rather than two because the issue asks for the exhaustive version, and eight rather than
     * twenty because the number is not a knob to turn until something breaks: what is under test is an
     * invariant, and a count tuned until it fails is how a flaky suite is born.
     */
    protected static final int BUYERS = 8;

    /**
     * This context is the one exception to {@code AbstractIntegrationTest.TEST_POOL_SIZE}, and the
     * reason is {@code CheckoutConcurrencyIT}.
     *
     * <p>Four connections are plenty for a suite whose tests run one request at a time — which every
     * other fixture does. Here {@link #BUYERS} checkouts are in flight together and all but one of them
     * are <em>blocked inside</em> {@code SELECT … FOR UPDATE}, holding their connection while they wait
     * for the winner to commit. With the shared limit they would not be waiting on the row at all, they
     * would be waiting on the pool, and the loser of the race would be answered
     * {@code 500 INTERNAL_ERROR} after Hikari's acquisition timeout instead of
     * {@code 409 INSUFFICIENT_STOCK} — the suite would be testing the pool size.
     *
     * <p>Twelve leaves room for the fixture's own transaction and the request that wins. It is one
     * context out of the dozen the suite caches, so the total stays far below the container's
     * {@code max_connections} of 100 — which is the ceiling the shared limit exists to respect.
     */
    private static final int ORDERS_POOL_SIZE = BUYERS + 4;

    @DynamicPropertySource
    static void ordersEnvironment(DynamicPropertyRegistry registry) {
        useDatabase(registry, ORDERS_DATABASE);
        useJwks(registry);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> ORDERS_POOL_SIZE);
    }

    @Autowired
    protected RestTestClient restTestClient;

    @Autowired
    protected MutableClock clock;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seedTheShop() {
        clock.reset();

        inATransaction(() -> List.of(StoreMother.onlineStore(), StoreMother.barcelonaStore())
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
                            ProductMother.discontinuedChair(furniture))
                    .forEach(entityManager::persist);
        });

        long online = storeIdOf("Ukun Online");
        long barcelona = storeIdOf("Ukun Barcelona");

        inATransaction(() -> {
            entityManager.persist(StoreStockMother.stock(productIdOf(IN_STOCK), online, 7));
            entityManager.persist(StoreStockMother.stock(productIdOf(LAST_ONE), online, 1));
            entityManager.persist(StoreStockMother.stock(productIdOf(ONLY_IN_BARCELONA), barcelona, 5));
            entityManager.persist(StoreStockMother.stock(productIdOf(DISCONTINUED), online, 4));
        });

        inATransaction(() -> {
            entityManager.persist(CustomerMother.ada(ADA));
            entityManager.persist(CustomerMother.grace(GRACE));
        });
    }

    /**
     * One statement, ordered so nothing is left pinned: {@code address} holds a store's address and a
     * customer's, and the order tables point at products and customers that the same truncation clears.
     */
    @AfterEach
    void clearTheShop() {
        jdbc.execute(
                """
                TRUNCATE TABLE order_line, customer_order, idempotency_key, checkout_attempt_line,
                    checkout_attempt, cart_item, store_stock, product, product_type, customer, card,
                    store, address
                RESTART IDENTITY CASCADE
                """);
    }

    /**
     * A crowd of {@code count} customers, seeded on demand and returned in a stable order.
     *
     * <p>Not part of {@link #seedTheShop()}: only the concurrency suite needs them, and the other four
     * would pay for the inserts in every test of theirs. The identifiers are derived from the index
     * rather than random so that a failure names the same account twice running.
     *
     * <p>It lives here because {@code entityManager} is this class's, and it seeds through a mother for
     * the reason the skill {@code testing} gives: data a test depends on is built where the test can
     * read it, not in a script nobody opens next to the assertion.
     */
    protected List<UUID> seedBuyers(int count) {
        List<UUID> accountIds = IntStream.rangeClosed(1, count)
                .mapToObj(index -> UUID.fromString("8f2c1e40-0000-4000-8000-%012d".formatted(index)))
                .toList();

        inATransaction(() -> IntStream.range(0, count)
                .forEach(index -> entityManager.persist(CustomerMother.buyer(accountIds.get(index), index + 1))));

        return accountIds;
    }

    protected CheckoutAttemptResponse openAttempt(UUID accountId) {
        return restTestClient
                .post()
                .uri(ATTEMPTS)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(accountId)))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(CheckoutAttemptResponse.class)
                .returnResult()
                .getResponseBody();
    }

    /** The happy path end to end: fill a cart, open the checkout, confirm it. */
    protected OrderResponse placeOrder(UUID accountId, String sku, int quantity) {
        addToCart(accountId, productIdOf(sku), quantity);

        return confirm(accountId, openAttempt(accountId).attemptId());
    }

    protected OrderResponse confirm(UUID accountId, UUID attemptId) {
        return restTestClient
                .post()
                .uri(ORDERS)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(accountId)))
                .header(IDEMPOTENCY_KEY, attemptId.toString())
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(OrderResponse.class)
                .returnResult()
                .getResponseBody();
    }

    protected void addToCart(UUID accountId, long productId, int quantity) {
        restTestClient
                .put()
                .uri(CART_ITEM + productId)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(accountId)))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"quantity\": %d}".formatted(quantity))
                .exchange()
                .expectStatus()
                .isOk();
    }

    protected int onlineStockOf(String sku) {
        return jdbc.queryForObject(
                """
                SELECT quantity FROM store_stock
                 WHERE product_id = (SELECT id FROM product WHERE sku = ?)
                   AND store_id = (SELECT id FROM store WHERE is_online)
                """,
                Integer.class,
                sku);
    }

    protected static String employeeToken() {
        return employeeToken(EMPLOYEE, "employee@ukun.dev");
    }

}
