package dev.sandoretti.ukun.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.api.inventory.application.AvailabilityItem;
import dev.sandoretti.ukun.api.inventory.application.AvailabilityResponse;
import dev.sandoretti.ukun.api.stores.StoresIntegrationTest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code GET /api/availability} (RF-01, query Q2 of {@code docs/10-modelo-de-datos.md}).
 *
 * <p>The fixture reproduces the three situations the seed data creates on purpose, because they are
 * the three branches of the rule: a product with units, a product whose row sits at zero, and a
 * product with no row in this store at all. The last two are the ones RF-01 insists must not be
 * distinguishable from outside.
 *
 * <p>The products are inserted with SQL rather than through an object mother of the {@code catalog}
 * module, and that is the point rather than a shortcut: {@code inventory} knows a product as a
 * {@code Long} and nothing else (ADR-0017 §2). The rows exist here only because
 * {@code store_stock.product_id} has a foreign key to satisfy.
 */
class AvailabilityIT extends StoresIntegrationTest {

    private static final String AVAILABILITY = "/api/availability";

    private static final String IN_STOCK = "SOFA-CUERO";
    private static final String OUT_OF_STOCK = "LITERA-INFANTIL";
    private static final String NOT_SOLD_HERE = "MESA-CAFE";
    private static final String DISCONTINUED = "SILLA-RETIRADA";

    @BeforeEach
    void seedTheStock() {
        jdbc.update("INSERT INTO product_type (name) VALUES ('Sofás')");

        insertProduct(IN_STOCK, "Sofá de cuero", "399.99", true);
        insertProduct(OUT_OF_STOCK, "Litera infantil", "399.99", true);
        insertProduct(NOT_SOLD_HERE, "Mesa de café", "129.75", true);
        insertProduct(DISCONTINUED, "Silla retirada", "49.90", false);

        long online = storeIdOf("Ukun Online");

        inATransaction(() -> {
            entityManager.persist(StoreStockMother.stock(productIdOf(IN_STOCK), online, 7));
            entityManager.persist(StoreStockMother.outOfStock(productIdOf(OUT_OF_STOCK), online));
            entityManager.persist(StoreStockMother.stock(productIdOf(DISCONTINUED), online, 4));
        });
    }

    @AfterEach
    void clearTheStock() {
        jdbc.execute("TRUNCATE TABLE store_stock, product, product_type RESTART IDENTITY CASCADE");
    }

    /**
     * The assertion issue #77 asks for, and the whole reason the endpoint returns a list rather than a
     * map of the products it happened to find: three identifiers in, three entries out. Without the
     * absent one being reported as {@code false}, the frontend would have to decide for itself what a
     * missing key means — and "unknown" and "unavailable" look the same on a product card.
     */
    @Test
    @DisplayName("every requested product gets an entry, including the one with no stock row at all")
    void answersOneEntryPerRequestedProduct() {
        long online = storeIdOf("Ukun Online");

        AvailabilityResponse response =
                availabilityOf(online, productIdOf(IN_STOCK), productIdOf(OUT_OF_STOCK), productIdOf(NOT_SOLD_HERE));

        assertThat(response.storeId()).isEqualTo(online);
        assertThat(response.items())
                .containsExactly(
                        new AvailabilityItem(productIdOf(IN_STOCK), true),
                        new AvailabilityItem(productIdOf(OUT_OF_STOCK), false),
                        new AvailabilityItem(productIdOf(NOT_SOLD_HERE), false));
    }

    /**
     * The two ways of not having something are one answer. Stated separately from the test above
     * because it is the rule, not a property of that particular request: a row at zero and no row are
     * both {@code false}, and nothing in the response lets a caller tell them apart.
     */
    @Test
    @DisplayName("a row at zero and a missing row are the same answer")
    void doesNotDistinguishOutOfStockFromNotSoldHere() {
        List<AvailabilityItem> items = availabilityOf(
                        storeIdOf("Ukun Online"), productIdOf(OUT_OF_STOCK), productIdOf(NOT_SOLD_HERE))
                .items();

        assertThat(items).extracting(AvailabilityItem::available).containsOnly(false);
    }

    /**
     * A store that stocks none of the three. The same products that are available online come back
     * unavailable here, which is what makes the {@code store} parameter meaningful rather than
     * decorative.
     */
    @Test
    @DisplayName("availability is per store, not global")
    void answersForTheStoreThatWasAsked() {
        long barcelona = storeIdOf("Ukun Barcelona");

        assertThat(availabilityOf(barcelona, productIdOf(IN_STOCK)).items())
                .containsExactly(new AvailabilityItem(productIdOf(IN_STOCK), false));
    }

    /** No quantity anywhere in the payload: the number of units is the customer's own business (RF-01). */
    @Test
    @DisplayName("the response never carries the number of units")
    void neverExposesTheQuantity() {
        restTestClient
                .get()
                .uri(uriFor(storeIdOf("Ukun Online"), productIdOf(IN_STOCK)))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items[0].available")
                .isEqualTo(true)
                .jsonPath("$.items[0].quantity")
                .doesNotExist()
                .jsonPath("$.items[0].stock")
                .doesNotExist();
    }

    /**
     * The only {@code 404} this endpoint has. A typo in {@code ?store=} must not come back as a
     * well-formed response saying nothing is available anywhere — that looks like an answer, which is
     * worse than an error.
     */
    @Test
    @DisplayName("an unknown store is a 404 with the resource and id extensions")
    void rejectsAnUnknownStore() {
        restTestClient
                .get()
                .uri(uriFor(999_999L, productIdOf(IN_STOCK)))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("RESOURCE_NOT_FOUND")
                .jsonPath("$.resource")
                .isEqualTo("store")
                .jsonPath("$.id")
                .isEqualTo("999999");
    }

    /**
     * The fourth case, added by issue #122 and the one the row cannot answer on its own: four units in
     * the online store, and none of them for sale. Discontinuing takes a product out of the catalogue
     * without emptying the warehouse (ADR-0013), so this endpoint has to ask the catalogue too — before
     * it did not, and answered {@code true} for a product the cart and the checkout were both reporting
     * as zero.
     */
    @Test
    @DisplayName("a discontinued product with units in the store is not available")
    void doesNotOfferADiscontinuedProduct() {
        assertThat(availabilityOf(storeIdOf("Ukun Online"), productIdOf(DISCONTINUED)).items())
                .containsExactly(new AvailabilityItem(productIdOf(DISCONTINUED), false));
    }

    /**
     * An identifier that is no product at all is unavailable, not an error: from the shop's side it is
     * the same thing.
     */
    @Test
    @DisplayName("an identifier matching no product is reported as unavailable, not as a 404")
    void reportsAnUnknownProductAsUnavailable() {
        assertThat(availabilityOf(storeIdOf("Ukun Online"), 999_999L).items())
                .containsExactly(new AvailabilityItem(999_999L, false));
    }

    /** {@code minItems: 1} in the contract. */
    @Test
    @DisplayName("a request with no products is rejected")
    void rejectsAnEmptyProductList() {
        restTestClient
                .get()
                .uri(AVAILABILITY + "?store=" + storeIdOf("Ukun Online"))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VALIDATION_FAILED");
    }

    /**
     * {@code maxItems: 100}. The bound is not decoration on an endpoint that needs no authentication:
     * without it one request turns an indexed batch look-up into a scan.
     */
    @Test
    @DisplayName("more than a hundred products is rejected")
    void rejectsAnOversizedBatch() {
        String products = String.join(",", Collections.nCopies(101, "1"));

        restTestClient
                .get()
                .uri(AVAILABILITY + "?store=" + storeIdOf("Ukun Online") + "&products=" + products)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VALIDATION_FAILED");
    }

    /** A missing {@code store} is a validation failure, not a guess at the online one. */
    @Test
    @DisplayName("a request with no store is rejected")
    void rejectsAMissingStore() {
        restTestClient
                .get()
                .uri(AVAILABILITY + "?products=" + productIdOf(IN_STOCK))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VALIDATION_FAILED");
    }

    /**
     * Three statements for any batch size: the store's existence check, the stock look-up and the
     * catalogue batch. The count is what proves the absent rows are reconstructed in memory rather than
     * probed one identifier at a time — the N+1 that a "one product at a time" implementation would
     * produce without ever looking wrong.
     *
     * <p><strong>It was two until issue #122.</strong> The third is {@code CatalogApi.summariesByIds},
     * and it is the price of this endpoint agreeing with the cart about a discontinued product
     * (ADR-0013). What matters here is that it is <em>one</em> statement and not one per product: the
     * cost of the fix does not grow with the batch, which is the property this test defends.
     */
    @Test
    @DisplayName("a batch of three products costs three statements, whatever the batch size")
    void resolvesTheWholeBatchInOneStatement() {
        long online = storeIdOf("Ukun Online");
        long inStock = productIdOf(IN_STOCK);
        long outOfStock = productIdOf(OUT_OF_STOCK);
        long notSoldHere = productIdOf(NOT_SOLD_HERE);

        Statistics statistics = freshStatistics();

        assertThat(availabilityOf(online, inStock, outOfStock, notSoldHere).items()).hasSize(3);

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    /**
     * The parameter that has to survive intact is {@code products}: the contract declares
     * {@code style: form, explode: false}, and a document that published it exploded would generate a
     * client sending {@code ?products=1&products=2}. This endpoint would still answer — Spring binds
     * both spellings — so nothing would fail until something on the other side of the contract cared.
     *
     * <p>{@code explode} is asserted because springdoc does not publish it on its own at any setting,
     * which is why {@code AvailabilityParameterStyle} exists. This assertion is what keeps that bean
     * honest: delete it and the test goes red instead of the document going quietly wrong.
     */
    @Test
    @DisplayName("the generated OpenAPI declares getAvailability with the parameters of the contract")
    void publishesTheContractParameters() {
        String products = "$.paths['/api/availability'].get.parameters[?(@.name=='products')]";

        restTestClient
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.paths['/api/availability'].get.operationId")
                .isEqualTo("getAvailability")
                .jsonPath("$.paths['/api/availability'].get.parameters[*].name")
                .value((List<String> names) -> assertThat(names).containsExactlyInAnyOrder("store", "products"))
                .jsonPath("$.paths['/api/availability'].get.parameters[?(@.name=='store')].required")
                .isEqualTo(true)
                .jsonPath(products + ".required")
                .isEqualTo(true)
                .jsonPath(products + ".style")
                .isEqualTo("form")
                .jsonPath(products + ".explode")
                .isEqualTo(false)
                .jsonPath(products + ".schema.minItems")
                .isEqualTo(1)
                .jsonPath(products + ".schema.maxItems")
                .isEqualTo(100);
    }

    private AvailabilityResponse availabilityOf(long storeId, Long... productIds) {
        return restTestClient
                .get()
                .uri(uriFor(storeId, productIds))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(AvailabilityResponse.class)
                .returnResult()
                .getResponseBody();
    }

    /** Comma-separated, as {@code style: form, explode: false} declares in the contract. */
    private String uriFor(long storeId, Long... productIds) {
        return "%s?store=%d&products=%s"
                .formatted(
                        AVAILABILITY,
                        storeId,
                        Arrays.stream(productIds).map(String::valueOf).collect(Collectors.joining(",")));
    }

    private void insertProduct(String sku, String name, String price, boolean active) {
        jdbc.update(
                """
                INSERT INTO product (sku, name, description, price, product_type_id, is_active)
                     VALUES (?, ?, ?, CAST(? AS DECIMAL), (SELECT id FROM product_type WHERE name = 'Sofás'), ?)
                """,
                sku,
                name,
                name + ", for testing purposes.",
                price,
                active);
    }

}
