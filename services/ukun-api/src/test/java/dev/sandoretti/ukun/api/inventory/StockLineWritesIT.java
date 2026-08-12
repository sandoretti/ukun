package dev.sandoretti.ukun.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Creating, editing and deleting a stock line (RF-09) — the write half of the employee's screen.
 *
 * <p>{@link #rejectsAnEditCarryingAStaleVersion()} is the assertion this suite exists for: it is the
 * optimistic concurrency of ADR-0008 as the client meets it, and the difference between two employees
 * overwriting each other in silence and the second one being told to look again. The delete tests
 * carry the other rule worth pinning down — removing a line is not setting it to zero, and the two
 * leave the database in states that are visibly different.
 */
class StockLineWritesIT extends StockIntegrationTest {

    @Test
    @DisplayName("creates a line by SKU and answers with its own URL")
    void createsALineBySku() {
        long alcala = alcalaId();

        String location = restTestClient
                .post()
                .uri(stockOf(alcala))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(ANA)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("sku", NEVER_STOCKED, "quantity", 12))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.sku")
                .isEqualTo(NEVER_STOCKED)
                .jsonPath("$.quantity")
                .isEqualTo(12)
                .jsonPath("$.version")
                .isEqualTo(0)
                .jsonPath("$.lineId")
                .isNotEmpty()
                .returnResult()
                .getResponseHeaders()
                .getFirst(HttpHeaders.LOCATION);

        assertThat(location).isEqualTo(lineOf(alcala, lineIdOf(NEVER_STOCKED, alcala)));
        assertThat(skusOf(stockOf(alcala), ANA)).contains(NEVER_STOCKED);
    }

    /** Zero units is a line the store keeps and has none of — a legitimate thing to create. */
    @Test
    @DisplayName("accepts a new line at zero units")
    void createsALineAtZero() {
        restTestClient
                .post()
                .uri(stockOf(alcalaId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(ANA)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("sku", NEVER_STOCKED, "quantity", 0))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.quantity")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("rejects a second line for a product this store already stocks")
    void rejectsADuplicateLine() {
        restTestClient
                .post()
                .uri(stockOf(alcalaId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(ANA)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("sku", STOCKED_SOFA, "quantity", 3))
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("REFERENTIAL_INTEGRITY")
                .jsonPath("$.traceId")
                .isNotEmpty();
    }

    @Test
    @DisplayName("rejects a SKU no product carries")
    void rejectsAnUnknownSku() {
        restTestClient
                .post()
                .uri(stockOf(alcalaId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(ANA)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("sku", "NO-EXISTE-1", "quantity", 3))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("RESOURCE_NOT_FOUND")
                .jsonPath("$.resource")
                .isEqualTo("Product");
    }

    /**
     * The rule that makes discontinuing mean something operationally (ADR-0013): a product taken out of
     * the catalogue cannot be given stock again. It answers {@code 404} rather than a conflict, because
     * from the employee's side there is no such product to stock.
     */
    @Test
    @DisplayName("refuses to stock a discontinued product, though the SKU exists")
    void refusesToStockADiscontinuedProduct() {
        long barcelona = storeIdOf(BARCELONA);

        restTestClient
                .post()
                .uri(stockOf(barcelona))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(BRUNO)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("sku", DISCONTINUED, "quantity", 3))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    @DisplayName("rejects a malformed SKU without looking for it")
    void rejectsAMalformedSku() {
        restTestClient
                .post()
                .uri(stockOf(alcalaId()))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(ANA)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("sku", "minúsculas y espacios", "quantity", 3))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors[0].field")
                .isEqualTo("sku");
    }

    @Test
    @DisplayName("rejects a negative quantity")
    void rejectsANegativeQuantity() {
        long alcala = alcalaId();

        restTestClient
                .put()
                .uri(lineOf(alcala, lineIdOf(STOCKED_SOFA, alcala)))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(ANA)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("quantity", -1, "version", 0))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("changes the quantity and hands back the new version")
    void updatesTheQuantity() {
        long alcala = alcalaId();
        long line = lineIdOf(STOCKED_SOFA, alcala);

        restTestClient
                .put()
                .uri(lineOf(alcala, line))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(ANA)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("quantity", 42, "version", 0))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.quantity")
                .isEqualTo(42)
                .jsonPath("$.version")
                .isEqualTo(1)
                .jsonPath("$.productName")
                .isEqualTo("Sofá de cuero");

        assertThat(quantityOf(line)).isEqualTo(42);
    }

    /**
     * ADR-0008, from the losing employee's side: they read the line at version 0, somebody else wrote
     * it, and their own write is refused instead of silently winning. The {@code id} extension is what
     * lets the frontend reload that exact line rather than the whole page.
     */
    @Test
    @DisplayName("rejects an edit carrying a stale version, leaving the row as it was")
    void rejectsAnEditCarryingAStaleVersion() {
        long alcala = alcalaId();
        long line = lineIdOf(STOCKED_SOFA, alcala);
        put(alcala, line, 10, 0).expectStatus().isOk();

        put(alcala, line, 99, 0)
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VERSION_CONFLICT")
                .jsonPath("$.resource")
                .isEqualTo("StockLine")
                .jsonPath("$.id")
                .isEqualTo(String.valueOf(line));

        assertThat(quantityOf(line)).isEqualTo(10);
    }

    @Test
    @DisplayName("answers 404 for a line of another store, the same as for one that does not exist")
    void hidesAnotherStoresLineBehindTheSame404() {
        long alcala = alcalaId();
        long barcelonasLine = lineIdOf(ONLY_IN_BARCELONA, storeIdOf(BARCELONA));

        put(alcala, barcelonasLine, 1, 0).expectStatus().isNotFound();
        put(alcala, 987654L, 1, 0).expectStatus().isNotFound();
    }

    /**
     * Deleting removes the row; setting the quantity to zero does not. The store that deleted a line is
     * offered the product back in {@code NOT_STOCKED}, which is the difference made visible.
     */
    @Test
    @DisplayName("deleting a line removes the row and offers the product back as not stocked")
    void deletesTheLine() {
        long alcala = alcalaId();
        long line = lineIdOf(STOCKED_SOFA, alcala);

        restTestClient
                .delete()
                .uri(lineOf(alcala, line))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(ANA)))
                .exchange()
                .expectStatus()
                .isNoContent();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM store_stock WHERE id = ?", Integer.class, line))
                .isZero();
        assertThat(skusOf(stockOf(alcala) + "?status=NOT_STOCKED", ANA)).contains(STOCKED_SOFA);
    }

    @Test
    @DisplayName("deleting a line that does not exist is a 404")
    void rejectsDeletingAMissingLine() {
        restTestClient
                .delete()
                .uri(lineOf(alcalaId(), 987654L))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(ANA)))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("RESOURCE_NOT_FOUND")
                .jsonPath("$.resource")
                .isEqualTo("StockLine");
    }

    private RestTestClient.ResponseSpec put(long storeId, long lineId, int quantity, long version) {

        return restTestClient
                .put()
                .uri(lineOf(storeId, lineId))
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(ANA)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("quantity", quantity, "version", version))
                .exchange();
    }

    private int quantityOf(long lineId) {
        return jdbc.queryForObject("SELECT quantity FROM store_stock WHERE id = ?", Integer.class, lineId);
    }
}
