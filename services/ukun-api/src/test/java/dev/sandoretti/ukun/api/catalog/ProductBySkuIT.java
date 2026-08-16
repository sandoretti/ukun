package dev.sandoretti.ukun.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * {@code GET /api/products/by-sku/{sku}} (RF-09, {@code getProductBySku} of the contract): the SKU an
 * employee reads off the label, resolved before the stock line is created with
 * {@code POST /api/stores/{storeId}/stock} (ADR-0015).
 *
 * <p>It answers the same {@code ProductDetail} as {@code getProduct}, so what is tested here is what
 * the key changes and nothing else: that the look-up is by SKU, that a discontinued product is a
 * {@code 404} — the rule that stops stock being added to something withdrawn from the catalogue
 * (ADR-0013) — and that the type still travels in the same statement.
 */
class ProductBySkuIT extends CatalogIntegrationTest {

    private static final String BY_SKU = "/api/products/by-sku/";

    /** The response as the frontend generates it from the contract. {@code price} is a string (Money). */
    private record Detail(
            Long id,
            String sku,
            String name,
            String price,
            Long typeId,
            String typeName,
            String imageUrl,
            String description) {}

    /**
     * The whole shape at once, asserted on the raw JSON rather than on the record, so a field the
     * contract does not declare fails here instead of being dropped silently on deserialisation. The
     * three {@code doesNotExist()} are the same three {@code getProduct} guards: {@code available}
     * belongs to {@code GET /api/availability} (ADR-0017 §3), {@code imageKey} is an internal storage
     * detail ({@code docs/08} §9), and {@code isActive} is back-office state a public read does not
     * announce.
     */
    @Test
    @DisplayName("the SKU printed on the label resolves to the product detail")
    void resolvesAnActiveProductBySku() {
        restTestClient
                .get()
                .uri(BY_SKU + "MESA-CAFE")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.sku")
                .isEqualTo("MESA-CAFE")
                .jsonPath("$.name")
                .isEqualTo("Mesa de café")
                .jsonPath("$.price")
                .isEqualTo("129.75")
                .jsonPath("$.typeName")
                .isEqualTo("Mesas")
                .jsonPath("$.imageUrl")
                .isEqualTo("/media/products/mesa-cafe.jpg")
                .jsonPath("$.available")
                .doesNotExist()
                .jsonPath("$.imageKey")
                .doesNotExist()
                .jsonPath("$.isActive")
                .doesNotExist();
    }

    /**
     * The {@code 404} of {@code docs/12-error-contract.md} in full, with the SKU as the {@code id}
     * extension: the registry types it as text precisely so the same exception serves numeric ids,
     * SKUs and UUIDs.
     */
    @Test
    @DisplayName("a SKU that does not exist is a ProblemDetail with code RESOURCE_NOT_FOUND")
    void reportsAnUnknownSkuAsNotFound() {
        restTestClient
                .get()
                .uri(BY_SKU + "NO-EXISTE")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("RESOURCE_NOT_FOUND")
                .jsonPath("$.status")
                .isEqualTo(404)
                .jsonPath("$.resource")
                .isEqualTo("Product")
                .jsonPath("$.id")
                .isEqualTo("NO-EXISTE")
                .jsonPath("$.traceId")
                .isNotEmpty();
    }

    /**
     * The rule this endpoint exists to enforce (ADR-0013): the row is still there — discontinuation is
     * logical — and answering it would let an employee restock something the shop has pulled from the
     * catalogue. It must also be indistinguishable from a SKU that never existed, or the response tells
     * anyone who asks which codes the shop used to sell.
     */
    @Test
    @DisplayName("a discontinued product is a 404, exactly like a SKU that never existed")
    void refusesToResolveADiscontinuedProduct() {
        restTestClient
                .get()
                .uri(BY_SKU + "SILLA-RETIRADA")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("RESOURCE_NOT_FOUND")
                .jsonPath("$.resource")
                .isEqualTo("Product")
                .jsonPath("$.id")
                .isEqualTo("SILLA-RETIRADA")
                .jsonPath("$.name")
                .doesNotExist();
    }

    /**
     * A SKU is an exact match and not a search: the catalogue holds {@code MESA-CAFE}, and neither a
     * lowercase spelling nor a prefix of it resolves. The lowercase case is also the one that documents
     * why the handler carries no {@code @Pattern} — the contract declares no {@code 400} for this
     * operation, so a code that cannot exist answers like a code that does not exist.
     */
    @Test
    @DisplayName("the match is exact: neither a prefix nor a different case resolves")
    void matchesTheSkuExactly() {
        expectNotFound("mesa-cafe");
        expectNotFound("MESA");
    }

    /**
     * The same mechanical check {@code getProduct} has, for defect #12: this detail carries
     * {@code typeName} too, so without the {@code @EntityGraph} on {@code findWithProductTypeBySku}
     * resolving it would either fire a second {@code SELECT} or fail outright under
     * {@code open-in-view: false}. The plain {@code findBySku} that {@code inventory} uses deliberately
     * has no graph, which is why this read needs its own query rather than that one.
     *
     * <p>Exactly one statement, and {@code typeName} asserted next to the count because it is what makes
     * the count mean anything: if nothing read the association there would be no second query to detect.
     */
    @Test
    @DisplayName("the detail resolves the product type in the same query")
    void readsTheDetailInASingleStatement() {
        long expected = productIdOf("MESA-CAFE");
        Statistics statistics = freshStatistics();

        Detail coffeeTable = detailOf("MESA-CAFE");

        assertThat(coffeeTable.id()).as("the row the SKU selects").isEqualTo(expected);
        assertThat(coffeeTable.typeName()).isEqualTo("Mesas");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    /**
     * springdoc takes the {@code operationId} from the method name and the frontend names a generated
     * client function after it, so renaming the handler renames a function in the SPA — silently,
     * because the endpoint keeps working (the bug of {@code 33d6f19}).
     */
    @Test
    @DisplayName("the generated OpenAPI declares getProductBySku with its path parameter")
    void publishesTheContractOperation() {
        restTestClient
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.paths['/api/products/by-sku/{sku}'].get.operationId")
                .isEqualTo("getProductBySku")
                .jsonPath("$.paths['/api/products/by-sku/{sku}'].get.parameters[*].name")
                .value((List<String> names) -> assertThat(names).containsExactly("sku"));
    }

    private void expectNotFound(String sku) {
        restTestClient
                .get()
                .uri(BY_SKU + sku)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("RESOURCE_NOT_FOUND");
    }

    private Detail detailOf(String sku) {
        return restTestClient
                .get()
                .uri(BY_SKU + sku)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Detail.class)
                .returnResult()
                .getResponseBody();
    }
}
