package dev.sandoretti.ukun.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

/**
 * The back-office listing — {@code GET /api/admin/products} (RF-10).
 *
 * <p>What separates it from the public one is the single thing it exists for: it can show a
 * discontinued product. Everything else — the accent folding, the total ordering, the page shape — is
 * the same behaviour, reached through the same specifications, and these tests say so on purpose:
 * a second search implementation for the back office would be a second answer to "what does typing
 * {@code sofa} match".
 */
class AdminProductCatalogIT extends BackOfficeIntegrationTest {

    private static final String ADMIN_PRODUCTS = "/api/admin/products";

    private record Product(Long id, String sku, String name, String price, boolean isActive, String updatedAt) {}

    private record Page(List<Product> items, int page, int size, long totalElements, int totalPages) {}

    /**
     * Four of the five seeded products are active; {@code Silla retirada} is not. The default is
     * {@code ACTIVE} so that the commonest back-office screen agrees with the public catalogue.
     */
    @Test
    @DisplayName("without status, the listing shows what the shop shows")
    void defaultsToActiveProducts() {
        assertThat(pageOf("").items()).hasSize(4).extracting(Product::isActive).containsOnly(true);
    }

    @Test
    @DisplayName("status=DISCONTINUED shows exactly what the public catalogue hides")
    void listsDiscontinuedProducts() {
        assertThat(pageOf("?status=DISCONTINUED").items())
                .extracting(Product::sku)
                .containsExactly("SILLA-RETIRADA");
    }

    @Test
    @DisplayName("status=ALL shows both, which is the only way to find everything")
    void listsEveryProduct() {
        assertThat(pageOf("?status=ALL").items()).hasSize(5);
    }

    /**
     * The SKU filter resolves a discontinued product, unlike {@code GET /api/products/by-sku/{sku}} —
     * with {@code status=ALL}, since the filters compose rather than override one another.
     */
    @Test
    @DisplayName("the sku filter is exact and reaches discontinued products")
    void filtersBySku() {
        assertThat(pageOf("?status=ALL&sku=SILLA-RETIRADA").items())
                .extracting(Product::name)
                .containsExactly("Silla retirada");
    }

    /** Defect #22, in the back office too: nobody types the accent when searching for their own stock. */
    @Test
    @DisplayName("searching by name ignores accents")
    void searchesIgnoringAccents() {
        assertThat(pageOf("?q=lampara").items()).extracting(Product::sku).containsExactly("LAMPARA-PIE");
    }

    /**
     * {@code Sofá de cuero} and {@code Litera infantil} both cost {@code 399.99} — the tie of defect #23.
     * The order is total because {@code id} is appended to every criterion, so the two pages of a
     * two-by-two pagination are disjoint and cover everything.
     */
    @Test
    @DisplayName("a price tie still paginates without repeating or losing a row")
    void paginatesTotally() {
        List<String> first = pageOf("?sort=PRICE_DESC&size=2&page=0").items().stream()
                .map(Product::sku)
                .toList();
        List<String> second = pageOf("?sort=PRICE_DESC&size=2&page=1").items().stream()
                .map(Product::sku)
                .toList();

        assertThat(first).hasSize(2).doesNotContainAnyElementsOf(second);
    }

    @Test
    @DisplayName("UPDATED_DESC is the back-office's own order, and the public listing has no such value")
    void sortsByLastChange() {
        assertThat(pageOf("?sort=UPDATED_DESC").items()).hasSize(4).extracting(Product::updatedAt).doesNotContainNull();
    }

    @Test
    @DisplayName("the row carries the management state the public detail hides")
    void exposesTheAdminShape() {
        restTestClient
                .get()
                .uri(ADMIN_PRODUCTS + "?status=DISCONTINUED")
                .header("Authorization", admin())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items[0].isActive")
                .isEqualTo(false)
                .jsonPath("$.items[0].description")
                .isNotEmpty()
                .jsonPath("$.items[0].createdAt")
                .isNotEmpty()
                .jsonPath("$.items[0].typeName")
                .isEqualTo(TABLES);
    }

    /**
     * Two statements for a page of rows that all render {@code typeName}: the read and its
     * {@code count}. Without the {@code @EntityGraph} the repository declares, each row would fetch its
     * product type in a query of its own — defect #12, in the second listing that renders one.
     *
     * <p>The {@code size} is explicit because Spring Data skips the {@code count} when a page comes back
     * whole, which would make the expected number depend on how many products the fixture happens to
     * seed. With a page smaller than the result, both statements are always issued and the assertion
     * measures what it is about.
     */
    @Test
    @DisplayName("the listing does not fetch a type per row")
    void doesNotFetchATypePerRow() {
        var statistics = freshStatistics();

        pageOf("?status=ALL&size=2");

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    /** A discontinued product is a {@code 404} for the shop and a {@code 200} here — the same row. */
    @Test
    @DisplayName("the detail resolves a discontinued product the public one refuses")
    void readsADiscontinuedProduct() {
        long id = productIdOf("SILLA-RETIRADA");

        restTestClient
                .get()
                .uri(ADMIN_PRODUCTS + "/" + id)
                .header("Authorization", admin())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.isActive")
                .isEqualTo(false);

        restTestClient
                .get()
                .uri("/api/products/" + id)
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    private Page pageOf(String query) {
        return restTestClient
                .get()
                .uri(ADMIN_PRODUCTS + query)
                .header("Authorization", admin())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(new ParameterizedTypeReference<Page>() {})
                .returnResult()
                .getResponseBody();
    }
}
