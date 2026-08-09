package dev.sandoretti.ukun.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** {@code GET /api/products/{id}} (RF-01, {@code getProduct} of the contract). */
class ProductDetailIT extends CatalogIntegrationTest {

    private static final String CATALOG = "/api/products";

    /**
     * The response as the frontend will generate it from the contract, so a renamed field fails here
     * rather than in the browser. {@code price} is {@code String} because the {@code Money} schema
     * says so.
     */
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
     * The whole shape at once, on the raw JSON. {@code price} is compared against a <em>string</em> on
     * purpose: the {@code Money} schema forbids a JSON number, because that is how the legacy lost
     * cents to {@code Float} (defect #5).
     *
     * <p>The four {@code doesNotExist()} are the point of the test as much as the values are.
     * {@code available} belongs to {@code GET /api/availability} — {@code catalog} cannot depend on
     * {@code inventory} (ADR-0017 §3). {@code imageKey} is an internal storage detail ({@code docs/08}
     * §9). {@code isActive} and {@code updatedAt} are {@code AdminProduct}, which is v0.7: a public
     * read does not announce the management state of a product.
     */
    @Test
    @DisplayName("the detail is the summary plus a description, and nothing else")
    void exposesTheContractShapeOfTheDetail() {
        restTestClient
                .get()
                .uri(CATALOG + "/" + productIdOf("SOFA-CUERO"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.sku")
                .isEqualTo("SOFA-CUERO")
                .jsonPath("$.name")
                .isEqualTo("Sofá de cuero")
                .jsonPath("$.price")
                .isEqualTo("399.99")
                .jsonPath("$.typeName")
                .isEqualTo("Sofás")
                .jsonPath("$.imageUrl")
                .isEqualTo("/media/products/sofa-cuero.jpg")
                .jsonPath("$.description")
                .isEqualTo("Sofá de cuero, for testing purposes.")
                .jsonPath("$.available")
                .doesNotExist()
                .jsonPath("$.imageKey")
                .doesNotExist()
                .jsonPath("$.isActive")
                .doesNotExist()
                .jsonPath("$.updatedAt")
                .doesNotExist();
    }

    @Test
    @DisplayName("a product without a photo reports a null imageUrl, not a URL pointing nowhere")
    void reportsANullImageUrlWhenThereIsNoPhoto() {
        Detail bunkBed = detailOf(productIdOf("LITERA-INFANTIL"));

        assertThat(bunkBed.imageUrl()).isNull();
        assertThat(bunkBed.typeName()).isEqualTo("Camas");
    }

    /**
     * The {@code 404} of {@code docs/12-error-contract.md}, in full. {@code code} is what the frontend
     * switches on, and {@code resource}/{@code id} are the extensions the registry declares for
     * {@code RESOURCE_NOT_FOUND} — {@code id} as text, so the same exception serves numeric ids, SKUs
     * and UUIDs.
     */
    @Test
    @DisplayName("an id that does not exist is a ProblemDetail with code RESOURCE_NOT_FOUND")
    void reportsAnUnknownIdAsNotFound() {
        restTestClient
                .get()
                .uri(CATALOG + "/999999")
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
                .isEqualTo("999999")
                .jsonPath("$.traceId")
                .isNotEmpty();
    }

    /**
     * ADR-0013: discontinuation is logical, so the row is still there and answering it would be the
     * easy mistake. As far as the public catalog is concerned the product does not exist, and it must
     * be indistinguishable from an id that never did — otherwise the response tells anyone who asks
     * which SKUs the shop used to sell.
     */
    @Test
    @DisplayName("a discontinued product is a 404, exactly like an id that never existed")
    void reportsADiscontinuedProductAsNotFound() {
        long discontinued = productIdOf("SILLA-RETIRADA");

        restTestClient
                .get()
                .uri(CATALOG + "/" + discontinued)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("RESOURCE_NOT_FOUND")
                .jsonPath("$.resource")
                .isEqualTo("Product")
                .jsonPath("$.id")
                .isEqualTo(String.valueOf(discontinued));
    }

    /**
     * The same mechanical check the listing has, for the same defect (#12) in its smaller shape: the
     * detail carries {@code typeName} too, so without the {@code @EntityGraph} on {@code findById}
     * resolving it would either fire a second {@code SELECT} or fail outright under
     * {@code open-in-view: false}.
     *
     * <p>Exactly one statement, not "at most a few". {@code typeName} is asserted next to the count
     * because it is what makes the count mean anything: if nothing read the association there would be
     * no second query to detect.
     */
    @Test
    @DisplayName("the detail resolves the product type in the same query")
    void readsTheDetailInASingleStatement() {
        long id = productIdOf("MESA-CAFE");
        Statistics statistics = freshStatistics();

        Detail coffeeTable = detailOf(id);

        assertThat(coffeeTable.typeName()).isEqualTo("Mesas");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a non-numeric id is a field error, never a 500")
    void rejectsANonNumericId() {
        restTestClient
                .get()
                .uri(CATALOG + "/not-a-number")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors[0].field")
                .isEqualTo("id");
    }

    /**
     * The generated document has to name the operation the way the frozen contract does: springdoc
     * takes {@code operationId} from the method name, and the frontend generates the name of a client
     * function from it. Renaming the handler renames a function in the SPA — silently, because the
     * endpoint keeps working (the bug of {@code 33d6f19}).
     */
    @Test
    @DisplayName("the generated OpenAPI declares getProduct with its path parameter")
    void publishesTheContractOperation() {
        restTestClient
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.paths['/api/products/{id}'].get.operationId")
                .isEqualTo("getProduct")
                .jsonPath("$.paths['/api/products/{id}'].get.parameters[*].name")
                .value((List<String> names) -> assertThat(names).containsExactly("id"));
    }

    private Detail detailOf(long id) {
        return restTestClient
                .get()
                .uri(CATALOG + "/" + id)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(Detail.class)
                .returnResult()
                .getResponseBody();
    }
}
