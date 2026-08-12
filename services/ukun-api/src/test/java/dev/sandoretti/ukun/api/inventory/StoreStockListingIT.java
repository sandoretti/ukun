package dev.sandoretti.ukun.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * {@code GET /api/stores/{storeId}/stock}, both halves of the employee's screen (RF-09, query Q5).
 *
 * <p>Two assertions here are the ones worth having. {@link #tellsALineAtZeroFromNoLineAtAll()} is the
 * distinction the whole {@code status} parameter exists for — a product the store keeps and has run
 * out of is not the same as one it does not carry — and it is the one a projection with primitive
 * fields would quietly destroy. {@link #pagesWithoutRepeatingOrLosingASharedName()} is defect #23: two
 * products with the same name really do exist in this data, and without {@code id} closing the
 * {@code ORDER BY} the second page can repeat what the first already showed.
 */
class StoreStockListingIT extends StockIntegrationTest {

    @Test
    @DisplayName("lists what the store holds, and nothing another store holds")
    void listsWhatTheStoreHolds() {
        long alcala = alcalaId();

        assertThat(skusOf(stockOf(alcala))).containsExactly(STOCKED_AT_ZERO, STOCKED_SOFA);
    }

    /**
     * The line at zero is in the list; the product with no line is not. Between them they are the whole
     * reason {@code quantity} is nullable rather than defaulted.
     */
    @Test
    @DisplayName("a line at zero is stocked; a product with no line is not")
    void tellsALineAtZeroFromNoLineAtAll() {
        long alcala = alcalaId();

        restTestClient
                .get()
                .uri(stockOf(alcala) + "?sku=" + STOCKED_AT_ZERO)
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(ANA)))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items.length()")
                .isEqualTo(1)
                .jsonPath("$.items[0].quantity")
                .isEqualTo(0)
                .jsonPath("$.items[0].lineId")
                .isNotEmpty();

        assertThat(skusOf(stockOf(alcala) + "?sku=" + NEVER_STOCKED)).isEmpty();
    }

    /**
     * The other half: what this store could add. The coffee table is stocked in Barcelona and appears
     * here all the same, which is what makes it a per-store anti-join rather than a global one.
     */
    @Test
    @DisplayName("NOT_STOCKED offers the catalogue this store has no line for, with null line fields")
    void offersWhatTheStoreDoesNotStockYet() {
        long alcala = alcalaId();

        assertThat(skusOf(stockOf(alcala) + "?status=NOT_STOCKED"))
                .containsExactly(NEVER_STOCKED, ONLY_IN_BARCELONA);

        restTestClient
                .get()
                .uri(stockOf(alcala) + "?status=NOT_STOCKED&sku=" + NEVER_STOCKED)
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(ANA)))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items[0].lineId")
                .isEmpty()
                .jsonPath("$.items[0].quantity")
                .isEmpty()
                .jsonPath("$.items[0].version")
                .isEmpty()
                .jsonPath("$.items[0].productId")
                .isNotEmpty()
                .jsonPath("$.items[0].productName")
                .isEqualTo("Lámpara de pie");
    }

    /**
     * A discontinued product is out of both halves (RF-10, ADR-0013): it cannot be restocked, and the
     * units it still holds are not shown as something to top up. Its stock row is untouched — the
     * fixture keeps four units of it — which is exactly why the filter has to be in the query rather
     * than in the data.
     */
    @Test
    @DisplayName("a discontinued product is in neither half, though its stock row still exists")
    void hidesDiscontinuedProductsFromBothHalves() {
        long alcala = alcalaId();

        assertThat(skusOf(stockOf(alcala))).doesNotContain(DISCONTINUED);
        assertThat(skusOf(stockOf(alcala) + "?status=NOT_STOCKED")).doesNotContain(DISCONTINUED);

        assertThat(jdbc.queryForObject(
                        "SELECT quantity FROM store_stock WHERE id = ?",
                        Integer.class,
                        lineIdOf(DISCONTINUED, alcala)))
                .isEqualTo(4);
    }

    /** Defect #22: the employee types what is on the keyboard, not what is on the label. */
    @Test
    @DisplayName("searching by name folds accents")
    void searchesByNameIgnoringAccents() {
        long alcala = alcalaId();

        assertThat(skusOf(stockOf(alcala) + "?q=sofa")).containsExactly(STOCKED_SOFA);
        assertThat(skusOf(stockOf(alcala) + "?status=NOT_STOCKED&q=cafe")).containsExactly(ONLY_IN_BARCELONA);
    }

    /**
     * Defect #23. Two products called the same thing are ordered by name first and by identifier after,
     * so paging through them shows each exactly once. Without the second criterion Postgres is free to
     * return them in either order per statement, and the two pages of this test may then share a row
     * and drop another.
     */
    @Test
    @DisplayName("pages a shared name without repeating or losing a row")
    void pagesWithoutRepeatingOrLosingASharedName() {
        long alcala = alcalaId();
        jdbc.update("INSERT INTO product_type (name) VALUES ('Sofás')");
        ProductRows.insert(jdbc, "GEMELO-A", "Sofá gemelo", "100.00", true);
        ProductRows.insert(jdbc, "GEMELO-B", "Sofá gemelo", "100.00", true);

        List<String> first = skusOf(stockOf(alcala) + "?status=NOT_STOCKED&q=gemelo&size=1&page=0");
        List<String> second = skusOf(stockOf(alcala) + "?status=NOT_STOCKED&q=gemelo&size=1&page=1");

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1).doesNotContainAnyElementsOf(first);
    }

    @Test
    @DisplayName("reports the totals of the page it answers")
    void reportsPageTotals() {
        restTestClient
                .get()
                .uri(stockOf(alcalaId()) + "?size=1")
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken(ANA)))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items.length()")
                .isEqualTo(1)
                .jsonPath("$.page")
                .isEqualTo(0)
                .jsonPath("$.size")
                .isEqualTo(1)
                .jsonPath("$.totalElements")
                .isEqualTo(2)
                .jsonPath("$.totalPages")
                .isEqualTo(2);
    }

    /**
     * The count does not grow with the rows, which is the whole of "no N+1" and the reason the
     * projection carries {@code sku} and {@code name} rather than asking {@code CatalogApi} for them
     * afterwards: a second read per line would show up here as the page doubled in size.
     *
     * <p>Asserted as "the same number twice" rather than as a literal, because the literal is not a
     * property of this endpoint: Spring Data skips the {@code COUNT} when the first page already holds
     * everything, so a fixture with one more product would change the expected figure without anything
     * being wrong. Two identical measurements over pages of different sizes cannot be satisfied by a
     * per-row query.
     */
    @Test
    @DisplayName("answers a page in the same number of statements however many rows it holds")
    void answersAPageWithoutQueryingPerRow() {
        long alcala = alcalaId();

        Statistics forTwoRows = freshStatistics();
        assertThat(skusOf(stockOf(alcala) + "?size=1")).hasSize(1);
        long statementsForTwoRows = forTwoRows.getPrepareStatementCount();

        stockTwoMoreProducts(alcala);

        Statistics forFourRows = freshStatistics();
        assertThat(skusOf(stockOf(alcala) + "?size=1")).hasSize(1);

        assertThat(forFourRows.getPrepareStatementCount()).isEqualTo(statementsForTwoRows);
    }

    /** Two more lines in this store, so the second measurement pages over twice the rows. */
    private void stockTwoMoreProducts(long storeId) {
        jdbc.update("INSERT INTO product_type (name) VALUES ('Sofás')");
        ProductRows.insert(jdbc, "EXTRA-A", "Sofá extra A", "100.00", true);
        ProductRows.insert(jdbc, "EXTRA-B", "Sofá extra B", "100.00", true);

        List.of("EXTRA-A", "EXTRA-B")
                .forEach(sku -> jdbc.update(
                        "INSERT INTO store_stock (product_id, store_id, quantity, version) VALUES (?, ?, 5, 0)",
                        productIdOf(sku),
                        storeId));
    }

    /** Ana's, always: what another employee sees is the subject of {@link StockAuthorizationIT}. */
    private List<String> skusOf(String uri) {
        return skusOf(uri, ANA);
    }
}
