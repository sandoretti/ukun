package dev.sandoretti.ukun.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

/** {@code GET /api/product-types} (RF-01, {@code listProductTypes} of the contract). */
class ProductTypeCatalogIT extends CatalogIntegrationTest {

    private static final String PRODUCT_TYPES = "/api/product-types";

    private record Type(Long id, String name) {}

    /**
     * {@code Camas}, {@code Mesas}, {@code Sofás} — alphabetical, and not the insertion order the
     * fixture uses ({@code Sofás}, {@code Camas}, {@code Mesas}). The mother seeds them out of order on
     * purpose: with a sorted fixture this test would pass against a repository that sorts by nothing at
     * all.
     *
     * <p>{@code Sofás} last is also what says the comparison is the database's collation and not a
     * byte-wise one that would have to be explained.
     */
    @Test
    @DisplayName("the types come back sorted by name, not in insertion order")
    void listsEveryTypeSortedByName() {
        assertThat(typesOf())
                .containsExactly(
                        new Type(typeIdOf("Camas"), "Camas"),
                        new Type(typeIdOf("Mesas"), "Mesas"),
                        new Type(typeIdOf("Sofás"), "Sofás"));
    }

    /**
     * Two fields and nothing else. {@code productCount}, {@code createdAt} and {@code updatedAt} are
     * {@code AdminProductType} (RF-13, v0.7): a dropdown does not need to know when a category was last
     * renamed, and counting products per type on every page load would be a join nobody asked for.
     */
    @Test
    @DisplayName("a type carries an id and a name, and nothing the back-office read adds")
    void exposesTheContractShapeOfAType() {
        restTestClient
                .get()
                .uri(PRODUCT_TYPES)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].name")
                .isEqualTo("Camas")
                .jsonPath("$[0].id")
                .isNotEmpty()
                .jsonPath("$[0].productCount")
                .doesNotExist()
                .jsonPath("$[0].createdAt")
                .doesNotExist();
    }

    /**
     * A flat array, not a page. The contract says so for a reason worth keeping honest: the SPA caches
     * the whole set, so {@code items}/{@code totalElements} would be a wrapper around a list that never
     * needs paging.
     */
    @Test
    @DisplayName("the response is a bare array, with no pagination wrapper")
    void returnsAFlatArray() {
        restTestClient
                .get()
                .uri(PRODUCT_TYPES)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$")
                .isArray()
                .jsonPath("$.items")
                .doesNotExist()
                .jsonPath("$.totalElements")
                .doesNotExist();
    }

    /**
     * The type of a discontinued product is still a type. {@code Mesas} holds the coffee table, the
     * floor lamp <em>and</em> {@code Silla retirada}; nothing here filters by
     * {@code product.is_active}, because ADR-0013 discontinues products, not categories — and a filter
     * that dropped an empty type would make the catalog's dropdown depend on the stock of the shop.
     */
    @Test
    @DisplayName("a type is listed on its own terms, not by what hangs off it")
    void listsTypesRegardlessOfTheirProducts() {
        assertThat(typesOf()).hasSize(3).extracting(Type::id).doesNotContainNull();
    }

    @Test
    @DisplayName("the generated OpenAPI declares listProductTypes with no parameters")
    void publishesTheContractOperation() {
        restTestClient
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.paths['/api/product-types'].get.operationId")
                .isEqualTo("listProductTypes")
                .jsonPath("$.paths['/api/product-types'].get.parameters")
                .doesNotExist();
    }

    private List<Type> typesOf() {
        return restTestClient
                .get()
                .uri(PRODUCT_TYPES)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(new ParameterizedTypeReference<List<Type>>() {})
                .returnResult()
                .getResponseBody();
    }
}
