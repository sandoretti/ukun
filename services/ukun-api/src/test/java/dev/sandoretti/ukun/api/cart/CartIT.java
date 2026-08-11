package dev.sandoretti.ukun.api.cart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import dev.sandoretti.ukun.api.AbstractIntegrationTest;
import dev.sandoretti.ukun.api.cart.application.CartLineResponse;
import dev.sandoretti.ukun.api.cart.application.CartResponse;
import dev.sandoretti.ukun.api.catalog.ProductMother;
import dev.sandoretti.ukun.api.catalog.domain.ProductType;
import dev.sandoretti.ukun.api.customers.CustomerMother;
import dev.sandoretti.ukun.api.inventory.StoreStockMother;
import dev.sandoretti.ukun.api.stores.StoreMother;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The four operations of {@code /api/cart} (RF-03), against a real database and real tokens.
 *
 * <p>Two assertions are the reason this suite exists rather than nice-to-haves. The first is
 * {@link #answersEachHolderTheirOwnCart()}: no route here carries a customer, so the only thing
 * deciding whose cart comes back is the {@code sub} claim resolved against {@code customer.account_id}
 * ({@code docs/08-identidad-y-seguridad.md} §4). The second is
 * {@link #acceptsMoreUnitsThanAreAvailableAndSaysSo()}, which is RF-03's "do not carry over" spelled
 * out: the legacy lets you put 999 units of a product with three in stock into your cart and says
 * nothing until checkout, and the fix is to <em>report</em> availability, not to start rejecting.
 *
 * <p>Real tokens rather than {@code SecurityMockMvcRequestPostProcessors.jwt()}, for the reason the
 * profile suites give: injecting an authentication into the context skips the one step where identity
 * is established, which is the step under test.
 *
 * <p>The fixture stocks the online store <em>and</em> Barcelona, with one product held only in
 * Barcelona. Without that, a cart reading stock from any store at all would pass every other assertion
 * here — RF-04's rule that the shop sells from the online store and nowhere else would be untested.
 */
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.liquibase.contexts=prod", "spring.jpa.properties.hibernate.generate_statistics=true"})
class CartIT extends AbstractIntegrationTest {

    private static final String CART = "/api/cart";
    private static final String CART_ITEM = "/api/cart/items/";

    private static final UUID ADA = UUID.fromString("8f2c1e40-0000-4000-8000-0000000000c1");
    private static final UUID GRACE = UUID.fromString("8f2c1e40-0000-4000-8000-0000000000c2");
    private static final UUID EMPLOYEE = UUID.fromString("8f2c1e40-0000-4000-8000-0000000000e1");

    /** Seven units online. */
    private static final String IN_STOCK = "SOFA-CUERO";

    /** A stock row sitting at zero, online. */
    private static final String OUT_OF_STOCK = "LITERA-INFANTIL";

    /** Five units, but in Barcelona: online it has no row at all. */
    private static final String ONLY_IN_BARCELONA = "MESA-CAFE";

    /** Discontinued, and stocked online all the same — discontinuing empties no warehouse (ADR-0013). */
    private static final String DISCONTINUED = "SILLA-RETIRADA";

    @DynamicPropertySource
    static void cartEnvironment(DynamicPropertyRegistry registry) {
        useDatabase(registry, CART_DATABASE);
        useJwks(registry);
    }

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private EntityManager entityManager;

    /** The module's own public API, as {@code orders} will hold it. */
    @Autowired
    private CartApi cartApi;

    @BeforeEach
    void seedTheShop() {
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
            entityManager.persist(StoreStockMother.outOfStock(productIdOf(OUT_OF_STOCK), online));
            entityManager.persist(StoreStockMother.stock(productIdOf(ONLY_IN_BARCELONA), barcelona, 5));
            entityManager.persist(StoreStockMother.stock(productIdOf(DISCONTINUED), online, 4));
        });

        inATransaction(() -> {
            entityManager.persist(CustomerMother.ada(ADA));
            entityManager.persist(CustomerMother.grace(GRACE));
        });
    }

    /**
     * One statement for the seven tables, because {@code address} holds both a store's address and a
     * customer's: truncating them separately would leave whichever went first pinned by the other's
     * {@code ON DELETE RESTRICT}.
     */
    @AfterEach
    void clearTheShop() {
        jdbc.execute(
                """
                TRUNCATE TABLE cart_item, store_stock, product, product_type, customer, card, store, address
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    @DisplayName("adding a product answers with the whole cart, priced and with its availability")
    void addsAProductAndAnswersWithTheWholeCart() {
        long sofa = productIdOf(IN_STOCK);

        restTestClient
                .put()
                .uri(CART_ITEM + sofa)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(quantity(2))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items.length()")
                .isEqualTo(1)
                .jsonPath("$.items[0].productId")
                .isEqualTo(sofa)
                .jsonPath("$.items[0].sku")
                .isEqualTo(IN_STOCK)
                .jsonPath("$.items[0].productName")
                .isEqualTo("Sofá de cuero")
                .jsonPath("$.items[0].imageUrl")
                .isEqualTo("/media/products/sofa-cuero.jpg")
                // Text and never a JSON number: the contract's Money schema, and defect #5.
                .jsonPath("$.items[0].unitPrice")
                .isEqualTo("399.99")
                .jsonPath("$.items[0].quantity")
                .isEqualTo(2)
                .jsonPath("$.items[0].lineTotal")
                .isEqualTo("799.98")
                .jsonPath("$.items[0].available")
                .isEqualTo(7)
                .jsonPath("$.total")
                .isEqualTo("799.98");
    }

    /**
     * The upsert of RF-03: one line per {@code (customer, product)}, and {@code quantity} is the line's
     * total rather than an increment. Sending the same body twice has to leave the same cart — that is
     * what makes the operation safe for the SPA to retry.
     */
    @Test
    @DisplayName("a second PUT replaces the quantity instead of adding to it")
    void replacesTheQuantityInsteadOfAddingToIt() {
        long sofa = productIdOf(IN_STOCK);

        putItem(ADA, sofa, 2);
        CartResponse cart = putItem(ADA, sofa, 5);

        assertSoftly(softly -> {
            softly.assertThat(cart.items()).hasSize(1);
            softly.assertThat(cart.items().getFirst().quantity()).isEqualTo(5);
            softly.assertThat(cart.total()).isEqualByComparingTo("1999.95");
        });
    }

    @Test
    @DisplayName("an empty cart is a 200 with no lines and a total of zero, not a 404")
    void answersAnEmptyCartWithAZeroTotal() {
        CartResponse cart = cartOf(ADA);

        assertSoftly(softly -> {
            softly.assertThat(cart.items()).isEmpty();
            softly.assertThat(cart.total()).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }

    /**
     * The photo comes down with the line (issue #120) and it is optional (RF-10), which is why both
     * cases are asserted together: what a screen needs to know is which of the two it got, and a
     * product with no photo has to say {@code null} rather than a URL pointing nowhere.
     *
     * <p>It arrives in the same read as everything else. The alternative from the browser would be one
     * {@code GET /api/products/{id}} per line to fetch a thumbnail, which is the N+1 that
     * {@link #readsTheWholeCartInAConstantNumberOfStatements()} exists to keep out of the server and
     * that {@code CatalogApi}'s batch-only signature keeps out of this module.
     */
    @Test
    @DisplayName("every line carries the product's photo, and null when the product has none")
    void carriesThePhotoOfEveryProduct() {
        putItem(ADA, productIdOf(IN_STOCK), 1);
        putItem(ADA, productIdOf(OUT_OF_STOCK), 1);

        assertThat(cartOf(ADA).items())
                .extracting(CartLineResponse::sku, CartLineResponse::imageUrl)
                .containsExactlyInAnyOrder(
                        tuple(IN_STOCK, "/media/products/sofa-cuero.jpg"), tuple(OUT_OF_STOCK, null));
    }

    /**
     * RF-04: the shop sells from the online store and from nowhere else. The mesa has five units in
     * Barcelona and no row online, which has to read as none available — otherwise the cart would be
     * promising stock the order cannot draw on.
     */
    @Test
    @DisplayName("availability is the online store's, and a missing row reads as zero")
    void reportsTheAvailabilityOfTheOnlineStore() {
        putItem(ADA, productIdOf(IN_STOCK), 1);
        putItem(ADA, productIdOf(OUT_OF_STOCK), 1);
        putItem(ADA, productIdOf(ONLY_IN_BARCELONA), 1);

        assertThat(cartOf(ADA).items())
                .extracting(CartLineResponse::sku, CartLineResponse::available)
                .containsExactlyInAnyOrder(tuple(IN_STOCK, 7), tuple(OUT_OF_STOCK, 0), tuple(ONLY_IN_BARCELONA, 0));
    }

    /**
     * The gap RF-03 marks as "do not carry over" — and the fix is to say so, not to refuse. Availability
     * is informational: it reserves nothing, and the stock check that can reject the purchase belongs to
     * {@code POST /api/orders} (RF-04).
     */
    @Test
    @DisplayName("asking for more units than are available is accepted, and the cart says how many there are")
    void acceptsMoreUnitsThanAreAvailableAndSaysSo() {
        CartResponse cart = putItem(ADA, productIdOf(IN_STOCK), 999);

        assertSoftly(softly -> {
            softly.assertThat(cart.items().getFirst().quantity()).isEqualTo(999);
            softly.assertThat(cart.items().getFirst().available()).isEqualTo(7);
        });
    }

    @Test
    @DisplayName("removing a line leaves the rest of the cart alone")
    void removesOneLine() {
        putItem(ADA, productIdOf(IN_STOCK), 1);
        putItem(ADA, productIdOf(OUT_OF_STOCK), 1);

        restTestClient
                .delete()
                .uri(CART_ITEM + productIdOf(IN_STOCK))
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA)))
                .exchange()
                .expectStatus()
                .isNoContent();

        assertThat(cartOf(ADA).items()).extracting(CartLineResponse::sku).containsExactly(OUT_OF_STOCK);
    }

    /**
     * The asymmetry with {@link #emptyingAnAlreadyEmptyCartIsAlsoA204()} is the contract's, and it is
     * deliberate: emptying asks for an outcome and gets it either way, while removing <em>this</em> line
     * asks about something that has to exist for the request to mean anything.
     */
    @Test
    @DisplayName("removing a line that is not there is a 404")
    void refusesToRemoveALineThatIsNotThere() {
        restTestClient
                .delete()
                .uri(CART_ITEM + productIdOf(IN_STOCK))
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA)))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("RESOURCE_NOT_FOUND");
    }

    /** The operation the legacy does not have at all (RF-03). */
    @Test
    @DisplayName("the whole cart is emptied in one request")
    void emptiesTheWholeCart() {
        putItem(ADA, productIdOf(IN_STOCK), 1);
        putItem(ADA, productIdOf(OUT_OF_STOCK), 3);

        emptyCart(ADA);

        assertThat(cartOf(ADA).items()).isEmpty();
    }

    @Test
    @DisplayName("emptying an already empty cart is also a 204")
    void emptyingAnAlreadyEmptyCartIsAlsoA204() {
        emptyCart(ADA);
        emptyCart(ADA);

        assertThat(cartOf(ADA).items()).isEmpty();
    }

    @Test
    @DisplayName("a product that does not exist cannot be added: 404")
    void refusesAProductThatDoesNotExist() {
        putItemExpecting404(ADA, 999_999L);
    }

    /**
     * A discontinued product is out of the public catalogue (ADR-0013), so as far as this API is
     * concerned there is nothing to add — the same judgement {@code GET /api/products/{id}} already
     * makes. Note that it is stocked: what takes it out of reach is the flag, not the warehouse.
     */
    @Test
    @DisplayName("a discontinued product cannot be added: 404")
    void refusesADiscontinuedProduct() {
        putItemExpecting404(ADA, productIdOf(DISCONTINUED));
    }

    /**
     * The line survives, because the contract has no field to flag it and has been frozen since SINC-1,
     * but it reports nothing available: whatever the warehouse holds, this is not for sale any more.
     */
    @Test
    @DisplayName("a line whose product is discontinued afterwards stays, with zero available")
    void keepsALineWhoseProductIsDiscontinuedAfterwards() {
        long sofa = productIdOf(IN_STOCK);
        putItem(ADA, sofa, 2);

        jdbc.update("UPDATE product SET is_active = false WHERE id = ?", sofa);

        CartLineResponse line = cartOf(ADA).items().getFirst();

        assertSoftly(softly -> {
            softly.assertThat(line.productId()).isEqualTo(sofa);
            softly.assertThat(line.quantity()).isEqualTo(2);
            softly.assertThat(line.available()).isZero();
        });
    }

    /**
     * Two customers, one URL, no identifier anywhere in it. If ownership were ever resolved from
     * something the caller sends, this is the test that would go red.
     */
    @Test
    @DisplayName("each token holder gets their own cart from the same URL")
    void answersEachHolderTheirOwnCart() {
        putItem(ADA, productIdOf(IN_STOCK), 2);
        putItem(GRACE, productIdOf(ONLY_IN_BARCELONA), 1);

        assertSoftly(softly -> {
            softly.assertThat(cartOf(ADA).items())
                    .extracting(CartLineResponse::sku)
                    .containsExactly(IN_STOCK);
            softly.assertThat(cartOf(GRACE).items())
                    .extracting(CartLineResponse::sku)
                    .containsExactly(ONLY_IN_BARCELONA);
        });
    }

    /**
     * The {@code productId} in the path identifies a line of the caller's own cart and authorises
     * nothing. Grace deleting the product Ada has in hers is a {@code 404} — there is no such line in
     * <em>her</em> cart — and Ada's is untouched.
     */
    @Test
    @DisplayName("one customer cannot remove a line from another customer's cart")
    void refusesToTouchSomebodyElsesLine() {
        long sofa = productIdOf(IN_STOCK);
        putItem(ADA, sofa, 2);

        restTestClient
                .delete()
                .uri(CART_ITEM + sofa)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(GRACE)))
                .exchange()
                .expectStatus()
                .isNotFound();

        assertThat(cartOf(ADA).items()).hasSize(1);
    }

    /** Emptying is scoped to the caller too, and it is the operation that could clear the whole table. */
    @Test
    @DisplayName("emptying a cart leaves everybody else's alone")
    void emptiesOnlyTheCallersCart() {
        putItem(ADA, productIdOf(IN_STOCK), 1);
        putItem(GRACE, productIdOf(IN_STOCK), 1);

        emptyCart(GRACE);

        assertThat(cartOf(ADA).items()).hasSize(1);
    }

    /**
     * An employee holds a valid token for this service and still has no cart here — the two roles are
     * mutually exclusive (ADR-0006) and this resource belongs to customers.
     */
    @Test
    @DisplayName("an employee is refused the cart with 403 FORBIDDEN")
    void refusesAnEmployee() {
        restTestClient
                .get()
                .uri(CART)
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken()))
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("without a token the cart is a 401 UNAUTHENTICATED")
    void refusesAnAnonymousCaller() {
        restTestClient
                .get()
                .uri(CART)
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UNAUTHENTICATED");
    }

    /**
     * Zero is not how a line is removed — {@code DELETE} is — and the {@code CHECK (quantity > 0)} in
     * the database must never be what rejects it: a constraint violation would surface as an accident
     * where the contract promises a {@code 400} naming the field.
     */
    @Test
    @DisplayName("a quantity of zero is a 400 VALIDATION_FAILED, not a removal")
    void rejectsAQuantityOfZero() {
        restTestClient
                .put()
                .uri(CART_ITEM + productIdOf(IN_STOCK))
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(quantity(0))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors[0].field")
                .isEqualTo("quantity");
    }

    /**
     * The anti-N+1 assertion of this slice. Reading a cart composes three modules — its own lines,
     * {@code CatalogApi} and {@code InventoryApi} — and the batch-only signatures of the last two exist
     * so that the cost does not grow with the cart. Ten lines must cost exactly what one does.
     *
     * <p>Five statements: the customer behind the token, the lines, the product batch, the online store
     * and the stock batch. The number is asserted as well as the equality, because "constant" alone
     * would still hold if somebody made it constant and expensive.
     */
    @Test
    @DisplayName("reading a cart costs the same five statements with one line and with ten")
    void readsTheWholeCartInAConstantNumberOfStatements() {
        putItem(ADA, productIdOf(IN_STOCK), 1);
        long withOneLine = statementsToRead(ADA, 1);

        fillTheCartOfAdaWithNineMoreProducts();
        long withTenLines = statementsToRead(ADA, 10);

        assertSoftly(softly -> {
            softly.assertThat(withOneLine).isEqualTo(5);
            softly.assertThat(withTenLines).isEqualTo(withOneLine);
        });
    }

    /** Reads the cart and reports what it cost, having checked it really held the lines it was meant to. */
    private long statementsToRead(UUID accountId, int expectedLines) {
        Statistics statistics = freshStatistics();

        assertThat(cartOf(accountId).items()).hasSize(expectedLines);

        return statistics.getPrepareStatementCount();
    }

    private void fillTheCartOfAdaWithNineMoreProducts() {
        inATransaction(() -> {
            ProductType filler = ProductMother.type("Relleno");
            entityManager.persist(filler);

            IntStream.range(0, 9)
                    .mapToObj(index -> ProductMother.filler(index, filler))
                    .forEach(entityManager::persist);
        });

        IntStream.range(0, 9).forEach(index -> putItem(ADA, productIdOf("FILLER-%d".formatted(index)), 1));
    }

    /**
     * The other half of the module: what {@code orders} will see through {@link CartApi} when checkout
     * turns a cart into an order (#34). Asserted here rather than left for that slice because an
     * interface nobody exercises is a promise, and this one has a rule in it — the lines come back in
     * the same stable order the response uses, so an order's lines are not shuffled either.
     */
    @Test
    @DisplayName("the module API hands checkout the lines of one customer, in a stable order")
    void handsTheLinesToCheckout() {
        putItem(ADA, productIdOf(OUT_OF_STOCK), 3);
        putItem(ADA, productIdOf(IN_STOCK), 1);
        putItem(GRACE, productIdOf(ONLY_IN_BARCELONA), 2);

        assertThat(cartApi.linesOf(customerIdOf(ADA)))
                .containsExactly(
                        new CartLineSnapshot(productIdOf(IN_STOCK), 1),
                        new CartLineSnapshot(productIdOf(OUT_OF_STOCK), 3));
    }

    /** RF-03: the cart is emptied when an order is completed, and only the buyer's. */
    @Test
    @DisplayName("the module API empties one customer's cart and leaves the rest alone")
    void clearsOneCartForCheckout() {
        putItem(ADA, productIdOf(IN_STOCK), 1);
        putItem(GRACE, productIdOf(IN_STOCK), 1);

        cartApi.clear(customerIdOf(ADA));

        assertSoftly(softly -> {
            softly.assertThat(cartApi.linesOf(customerIdOf(ADA))).isEmpty();
            softly.assertThat(cartApi.linesOf(customerIdOf(GRACE))).hasSize(1);
        });
    }

    private CartResponse cartOf(UUID accountId) {
        return restTestClient
                .get()
                .uri(CART)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(accountId)))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(CartResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private CartResponse putItem(UUID accountId, long productId, int quantity) {
        return restTestClient
                .put()
                .uri(CART_ITEM + productId)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(accountId)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(quantity(quantity))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(CartResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private void putItemExpecting404(UUID accountId, long productId) {
        restTestClient
                .put()
                .uri(CART_ITEM + productId)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(accountId)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(quantity(1))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("RESOURCE_NOT_FOUND")
                .jsonPath("$.resource")
                .isEqualTo("Product");
    }

    private void emptyCart(UUID accountId) {
        restTestClient
                .delete()
                .uri(CART)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(accountId)))
                .exchange()
                .expectStatus()
                .isNoContent();
    }

    private static String quantity(int quantity) {
        return "{\"quantity\": %d}".formatted(quantity);
    }

    private static String employeeToken() {
        return employeeToken(EMPLOYEE, "employee@ukun.dev");
    }

    /**
     * The crossing the endpoints never make in a test: from the account in the token to the row
     * {@code customers} owns. Only the {@link CartApi} assertions need it — everything else goes through
     * the token, which is the point.
     */
    private long customerIdOf(UUID accountId) {
        return jdbc.queryForObject("SELECT id FROM customer WHERE account_id = ?", Long.class, accountId);
    }
}
