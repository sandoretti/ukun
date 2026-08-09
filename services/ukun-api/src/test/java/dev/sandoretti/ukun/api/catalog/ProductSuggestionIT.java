package dev.sandoretti.ukun.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

/** {@code GET /api/products/suggest} (RF-01, {@code suggestProducts} of the contract). */
class ProductSuggestionIT extends CatalogIntegrationTest {

    private static final String SUGGEST = "/api/products/suggest";

    /** {@code price} is {@code String} because the {@code Money} schema forbids a JSON number. */
    private record Suggestion(Long id, String sku, String name, String price, String imageUrl) {}

    /**
     * The headline case of the issue, and defect #22 in the shape the search box meets it: the customer
     * types three letters without an accent and the catalogue is full of them. It works because the
     * endpoint reuses {@code ProductSpecifications.nameContains}, so {@code unaccent(lower(…))} is
     * applied on both sides — the same normalisation the listing does, not a second one.
     */
    @Test
    @DisplayName("\"sof\" suggests \"Sofá de cuero\"")
    void findsAnAccentedNameFromAnUnaccentedPrefix() {
        assertThat(suggestionsFor("?q=sof")).extracting(Suggestion::name).containsExactly("Sofá de cuero");
    }

    @Test
    @DisplayName("the search ignores case and matches anywhere in the name, exactly like the listing")
    void matchesTheSameWayTheListingDoes() {
        assertSoftly(softly -> {
            softly.assertThat(namesOf("?q=CUERO")).containsExactly("Sofá de cuero");
            softly.assertThat(namesOf("?q=lampara")).containsExactly("Lámpara de pie");
            softly.assertThat(namesOf("?q=de")).containsExactly("Lámpara de pie", "Mesa de café", "Sofá de cuero");
        });
    }

    /**
     * By name and total (defect #23). Asserted with {@code containsExactly} rather than
     * {@code containsExactlyInAnyOrder} on purpose: an unordered suggestion list reshuffles under the
     * cursor between two keystrokes that match the same rows, and combined with {@code limit} it would
     * return a different subset of a tie each time.
     *
     * <p>The expected order is <em>read from the database</em> instead of being spelled out as a
     * literal, because how {@code Lámpara} and {@code Litera} compare is the collation's business and
     * not ours — the same call {@code ProductSpecifications} already makes for accent folding. Under
     * the container's {@code C} locale the accented byte sorts last; under a Spanish collation it would
     * not. What this endpoint promises, and what is asserted here, is that its order <em>is</em> the
     * database's {@code ORDER BY name, id} — so the test stays true wherever it runs.
     */
    @Test
    @DisplayName("the suggestions come back in the database's order by name, tie-broken by id")
    void sortsSuggestionsByName() {
        List<String> asTheDatabaseOrdersThem =
                jdbc.queryForList("SELECT name FROM product WHERE is_active ORDER BY name, id", String.class);

        assertThat(namesOf("?q=a")).containsExactlyElementsOf(asTheDatabaseOrdersThem);
    }

    /** ADR-0013: a discontinued product is out of the catalogue, so it is out of the box that searches it. */
    @Test
    @DisplayName("a discontinued product is never suggested")
    void neverSuggestsADiscontinuedProduct() {
        assertThat(namesOf("?q=silla")).isEmpty();
    }

    /**
     * {@code limit} takes a <em>prefix</em> of the ordered result, not an arbitrary subset of it. That
     * is the half worth asserting: a cap applied before the sort would still return the right number of
     * rows and the wrong ones, and with four matches nobody would notice.
     */
    @Test
    @DisplayName("limit caps the suggestions, keeping the first ones in order")
    void respectsTheLimit() {
        List<String> everything = namesOf("?q=a");

        assertSoftly(softly -> {
            softly.assertThat(namesOf("?q=a&limit=1")).containsExactlyElementsOf(everything.subList(0, 1));
            softly.assertThat(namesOf("?q=a&limit=2")).containsExactlyElementsOf(everything.subList(0, 2));
        });
    }

    /**
     * The contract's default. Four active products match {@code "a"} and the default is {@code 8}, so
     * this asserts that omitting {@code limit} does not fall back to "everything" — the default is
     * declared, not the absence of a cap.
     */
    @Test
    @DisplayName("the generated OpenAPI declares limit's default of 8, and omitting it applies a cap")
    void appliesTheContractDefaultForLimit() {
        assertThat(namesOf("?q=a")).hasSize(4);

        restTestClient
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.paths['/api/products/suggest'].get.parameters[?(@.name=='limit')].schema.default")
                .isEqualTo(8);
    }

    /**
     * A flat array, which is the whole reason this endpoint exists next to {@code GET /api/products}
     * instead of being a preset of it: no {@code totalElements} means no {@code COUNT} on every
     * keystroke. A wrapper appearing here would be a silent performance regression, so it is asserted
     * rather than assumed.
     */
    @Test
    @DisplayName("the response is a bare array, with no pagination wrapper and no COUNT behind it")
    void returnsAFlatArray() {
        restTestClient
                .get()
                .uri(SUGGEST + "?q=sof")
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
     * The reduced shape of the contract. {@code typeId}/{@code typeName} must be absent: they are not
     * in {@code ProductSuggestion}, and their absence is what keeps the query from touching the
     * association at all.
     */
    @Test
    @DisplayName("a suggestion carries five fields and no product type")
    void exposesTheContractShapeOfASuggestion() {
        restTestClient
                .get()
                .uri(SUGGEST + "?q=cuero")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].sku")
                .isEqualTo("SOFA-CUERO")
                .jsonPath("$[0].name")
                .isEqualTo("Sofá de cuero")
                .jsonPath("$[0].price")
                .isEqualTo("399.99")
                .jsonPath("$[0].imageUrl")
                .isEqualTo("/media/products/sofa-cuero.jpg")
                .jsonPath("$[0].typeId")
                .doesNotExist()
                .jsonPath("$[0].typeName")
                .doesNotExist()
                .jsonPath("$[0].description")
                .doesNotExist();
    }

    @Test
    @DisplayName("a product without a photo suggests a null imageUrl, not a URL pointing nowhere")
    void reportsANullImageUrlWhenThereIsNoPhoto() {
        assertThat(suggestionsFor("?q=litera").getFirst().imageUrl()).isNull();
    }

    /**
     * {@code /suggest} is a literal segment and {@code /{id}} a template on the same path.
     * {@code PathPattern} prefers the literal, so this reaches the typeahead; if the two ever collided
     * the request would bind {@code "suggest"} to a {@code Long} and come back a {@code 400}. The
     * encargo asks for this to be verified and not assumed, and the second half is why: the collision
     * is invisible from the {@code getProduct} side, which keeps working.
     */
    @Test
    @DisplayName("/suggest wins over /{id}, and asking for it is not a malformed id")
    void doesNotCollideWithTheDetailRoute() {
        assertThat(namesOf("?q=sof")).isNotEmpty();

        restTestClient
                .get()
                .uri("/api/products/" + productIdOf("SOFA-CUERO"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.description")
                .isNotEmpty();
    }

    /** {@code q} is required (contract), so omitting it is a {@code 400} and not a listing of everything. */
    @Test
    @DisplayName("a missing q is a ProblemDetail with code VALIDATION_FAILED")
    void rejectsAMissingTerm() {
        restTestClient
                .get()
                .uri(SUGGEST)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.traceId")
                .isNotEmpty();
    }

    /**
     * The bounds of the contract, both ends. The upper one is what stops {@code ?limit=1000} from
     * turning a dropdown into an unbounded listing — the same reason {@code size} is capped on the
     * catalog.
     */
    @Test
    @DisplayName("an out-of-range limit is a field error naming limit")
    void rejectsAnOutOfRangeLimit() {
        expectFieldError("?q=sof&limit=0", "limit");
        expectFieldError("?q=sof&limit=11", "limit");
    }

    @Test
    @DisplayName("an empty q is a field error naming q, not a match against the whole catalogue")
    void rejectsAnEmptyTerm() {
        expectFieldError("?q=", "q");
    }

    @Test
    @DisplayName("the generated OpenAPI declares suggestProducts with the query parameters of the contract")
    void publishesTheContractParameters() {
        restTestClient
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.paths['/api/products/suggest'].get.operationId")
                .isEqualTo("suggestProducts")
                .jsonPath("$.paths['/api/products/suggest'].get.parameters[*].name")
                .value((List<String> names) -> assertThat(names).containsExactlyInAnyOrder("q", "limit"))
                .jsonPath(parameter("q") + ".required")
                .isEqualTo(true)
                .jsonPath(parameter("q") + ".schema.minLength")
                .isEqualTo(1)
                .jsonPath(parameter("q") + ".schema.maxLength")
                .isEqualTo(255)
                .jsonPath(parameter("limit") + ".schema.minimum")
                .isEqualTo(1)
                .jsonPath(parameter("limit") + ".schema.maximum")
                .isEqualTo(10);
    }

    private static String parameter(String name) {
        return "$.paths['/api/products/suggest'].get.parameters[?(@.name=='%s')]".formatted(name);
    }

    private List<Suggestion> suggestionsFor(String queryString) {
        return restTestClient
                .get()
                .uri(SUGGEST + queryString)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(new ParameterizedTypeReference<List<Suggestion>>() {})
                .returnResult()
                .getResponseBody();
    }

    private List<String> namesOf(String queryString) {
        return suggestionsFor(queryString).stream().map(Suggestion::name).toList();
    }

    /** A rejected parameter is the {@code ProblemDetail} of {@code docs/12} §1 with the field named. */
    private void expectFieldError(String queryString, String field) {
        restTestClient
                .get()
                .uri(SUGGEST + queryString)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors[0].field")
                .isEqualTo(field);
    }
}
