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

    @DynamicPropertySource
    static void ordersEnvironment(DynamicPropertyRegistry registry) {
        useDatabase(registry, ORDERS_DATABASE);
        useJwks(registry);
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

    /** Nobody here asserts on the email, so it follows from the account rather than being one more constant. */
    protected static String customerToken(UUID accountId) {
        return customerToken(accountId, accountId + "@ukun.dev");
    }

    protected static String employeeToken() {
        return employeeToken(EMPLOYEE, "employee@ukun.dev");
    }

}
