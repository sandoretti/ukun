package dev.sandoretti.ukun.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

/**
 * The writes of the back-office product CRUD — RF-10: creating, editing, deleting, and the two
 * operations that take a product out of the catalogue and put it back (ADR-0013).
 */
class AdminProductWritesIT extends BackOfficeIntegrationTest {

    private static final String ADMIN_PRODUCTS = "/api/admin/products";
    private static final String ONE_PRODUCT = ADMIN_PRODUCTS + "/{id}";

    @Test
    @DisplayName("a product is born active, without a photo, and answers Location")
    void createsAProduct() {
        String location = restTestClient
                .post()
                .uri(ADMIN_PRODUCTS)
                .header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON)
                .body(newProduct("BUTACA-001", typeIdOf(SOFAS)))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.isActive")
                .isEqualTo(true)
                // Present and null, not absent: the contract types it `[string, 'null']` and the SPA
                // reads the field rather than testing for its existence.
                .jsonPath("$.imageUrl")
                .isEmpty()
                .jsonPath("$.price")
                .isEqualTo("249.90")
                .jsonPath("$.createdAt")
                .isNotEmpty()
                .jsonPath("$.updatedAt")
                .isNotEmpty()
                .returnResult()
                .getResponseHeaders()
                .getFirst("Location");

        assertThat(location).isEqualTo(ADMIN_PRODUCTS + "/" + productIdOf("BUTACA-001"));
    }

    /**
     * <strong>A SKU identifies one product for life</strong> (ADR-0015), so the code of a withdrawn
     * product is not free to be reused: the label on the shelf does not know the product was
     * discontinued. This is the case the check would miss if it filtered by {@code is_active}.
     */
    @Test
    @DisplayName("the SKU of a discontinued product is still taken")
    void refusesToReuseTheSkuOfADiscontinuedProduct() {
        restTestClient
                .post()
                .uri(ADMIN_PRODUCTS)
                .header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON)
                .body(newProduct("SILLA-RETIRADA", typeIdOf(SOFAS)))
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("DUPLICATE_VALUE")
                .jsonPath("$.resource")
                .isEqualTo("product")
                .jsonPath("$.field")
                .isEqualTo("sku");
    }

    /** A type that does not exist is a {@code 404} naming it, not a referential integrity conflict. */
    @Test
    @DisplayName("an unknown typeId is a 404 for the type, not a 409")
    void refusesAnUnknownType() {
        restTestClient
                .post()
                .uri(ADMIN_PRODUCTS)
                .header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON)
                .body(newProduct("BUTACA-002", 4242L))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("RESOURCE_NOT_FOUND")
                .jsonPath("$.resource")
                .isEqualTo("productType");
    }

    /**
     * The three fields the request cannot carry stay as they were: the SKU because it is immutable, the
     * photo because it is a sub-resource, and {@code isActive} because withdrawing is its own operation.
     * A body that names them anyway is ignored — the record has nowhere to put them.
     */
    @Test
    @DisplayName("editing changes the four editable fields and nothing else")
    void updatesAProduct() {
        long id = productIdOf("MESA-CAFE");

        restTestClient
                .put()
                .uri(ONE_PRODUCT, id)
                .header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        """
                        {"name": "Mesa de café renovada", "description": "Otra descripción",
                         "price": "149.00", "typeId": %d, "sku": "OTRO-SKU", "isActive": false}
                        """
                                .formatted(typeIdOf(SOFAS)))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.name")
                .isEqualTo("Mesa de café renovada")
                .jsonPath("$.price")
                .isEqualTo("149.00")
                .jsonPath("$.typeName")
                .isEqualTo(SOFAS)
                .jsonPath("$.sku")
                .isEqualTo("MESA-CAFE")
                .jsonPath("$.isActive")
                .isEqualTo(true);
    }

    @Test
    @DisplayName("a product nothing references is really deleted")
    void deletesAnUnreferencedProduct() {
        long id = productIdOf("LAMPARA-PIE");

        restTestClient
                .delete()
                .uri(ONE_PRODUCT, id)
                .header("Authorization", admin())
                .exchange()
                .expectStatus()
                .isNoContent();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM product WHERE id = ?", Long.class, id))
                .isZero();
    }

    /**
     * The three counts always travel, including the zeros: "in one cart and no orders" is what tells the
     * admin whether waiting is worth anything, and a missing key would be indistinguishable from a zero.
     *
     * <p>This is also what proves the three counters are wired at all. They live in {@code inventory},
     * {@code cart} and {@code orders} and reach {@code catalog} through {@code ProductReferenceCounter},
     * so a module that stopped registering its own would show up here as a zero where the fixture put a
     * row.
     */
    @Test
    @DisplayName("a referenced product answers 409 saying where the references are")
    void refusesToDeleteAReferencedProduct() {
        long id = productIdOf("MESA-CAFE");
        seedAStockLineFor(id);
        seedACartLineFor(id);

        restTestClient
                .delete()
                .uri(ONE_PRODUCT, id)
                .header("Authorization", admin())
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("PRODUCT_IN_USE")
                .jsonPath("$.inStock")
                .isEqualTo(1)
                .jsonPath("$.inCarts")
                .isEqualTo(1)
                .jsonPath("$.inOrders")
                .isEqualTo(0);
    }

    /**
     * The count in the use case is the experience; the {@code RESTRICT} of ADR-0013 is the guarantee. It
     * holds against a statement typed straight into the database, which is the half a check inside the
     * application can never provide.
     */
    @Test
    @DisplayName("a referenced product cannot be deleted from the database either")
    void refusesToDeleteAReferencedProductEvenFromSql() {
        long id = productIdOf("MESA-CAFE");
        seedAStockLineFor(id);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM product WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Discontinuing is the real way out for a product that has been sold, and it leaves the stock rows
     * alone: the units stay recorded in each store, they simply stop being sellable (ADR-0013).
     */
    @Test
    @DisplayName("discontinuing removes the product from the shop without touching its stock")
    void discontinuesAProduct() {
        long id = productIdOf("MESA-CAFE");
        seedAStockLineFor(id);

        restTestClient
                .post()
                .uri(ONE_PRODUCT + ":discontinue", id)
                .header("Authorization", admin())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.isActive")
                .isEqualTo(false);

        restTestClient.get().uri("/api/products/" + id).exchange().expectStatus().isNotFound();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM store_stock WHERE product_id = ?", Long.class, id))
                .isEqualTo(1);
    }

    /** Idempotent, which is what a screen refreshed twice needs and what the contract promises. */
    @Test
    @DisplayName("discontinuing twice answers 200 and changes nothing")
    void discontinuingIsIdempotent() {
        long id = productIdOf("SILLA-RETIRADA");

        restTestClient
                .post()
                .uri(ONE_PRODUCT + ":discontinue", id)
                .header("Authorization", admin())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.isActive")
                .isEqualTo(false);
    }

    /**
     * Reactivating puts the product back in the catalogue. Availability is a different question, and the
     * product comes back with whatever stock it had — none, here.
     */
    @Test
    @DisplayName("reactivating puts a withdrawn product back in the public catalogue")
    void reactivatesAProduct() {
        long id = productIdOf("SILLA-RETIRADA");

        restTestClient
                .post()
                .uri(ONE_PRODUCT + ":reactivate", id)
                .header("Authorization", admin())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.isActive")
                .isEqualTo(true);

        restTestClient.get().uri("/api/products/" + id).exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("a customer's token never reaches the back-office")
    void refusesACustomer() {
        restTestClient
                .post()
                .uri(ADMIN_PRODUCTS)
                .header("Authorization", bearer(customerToken(java.util.UUID.randomUUID())))
                .contentType(MediaType.APPLICATION_JSON)
                .body(newProduct("BUTACA-003", typeIdOf(SOFAS)))
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    private static String newProduct(String sku, Long typeId) {
        return """
               {"sku": "%s", "name": "Butaca de lectura", "description": "Una butaca.",
                "price": "249.90", "typeId": %d}
               """
                .formatted(sku, typeId);
    }

    /**
     * A store and one line of stock for it, written with plain SQL rather than through the object
     * mothers of {@code stores} and {@code inventory}: what this suite needs is a row that makes the
     * count non-zero, and building a store through its own aggregate would drag an address, an employee
     * and a token into a test about deleting a product.
     */
    private void seedAStockLineFor(long productId) {
        jdbc.update(
                """
                INSERT INTO address (street, city, province, postal_code) VALUES ('Calle Mayor 1', 'Alcalá', 'Madrid', '28801')
                """);
        Long addressId = jdbc.queryForObject("SELECT max(id) FROM address", Long.class);

        jdbc.update("INSERT INTO store (name, phone, is_online, address_id) VALUES (?, ?, false, ?)",
                "Tienda de prueba", "+34918880001", addressId);
        Long storeId = jdbc.queryForObject("SELECT max(id) FROM store", Long.class);

        jdbc.update(
                "INSERT INTO store_stock (product_id, store_id, quantity, version) VALUES (?, ?, 3, 0)",
                productId,
                storeId);
    }

    /** The same idea for {@code cart_item}, which needs a customer to hang off. */
    private void seedACartLineFor(long productId) {
        jdbc.update(
                """
                INSERT INTO address (street, city, province, postal_code) VALUES ('Calle Menor 2', 'Madrid', 'Madrid', '28001')
                """);
        Long addressId = jdbc.queryForObject("SELECT max(id) FROM address", Long.class);

        jdbc.update("INSERT INTO card (last4, brand, exp_month, exp_year) VALUES ('4242', 'VISA', 12, 2030)");
        Long cardId = jdbc.queryForObject("SELECT max(id) FROM card", Long.class);

        jdbc.update(
                """
                INSERT INTO customer (account_id, name, last_name, email, locale, address_id, card_id)
                VALUES (gen_random_uuid(), 'Ada', 'Lovelace', 'ada@ukun.dev', 'es', ?, ?)
                """,
                addressId,
                cardId);
        Long customerId = jdbc.queryForObject("SELECT max(id) FROM customer", Long.class);

        jdbc.update("INSERT INTO cart_item (customer_id, product_id, quantity) VALUES (?, ?, 2)", customerId, productId);
    }
}
