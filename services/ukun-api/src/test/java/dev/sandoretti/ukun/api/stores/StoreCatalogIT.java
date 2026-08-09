package dev.sandoretti.ukun.api.stores;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import dev.sandoretti.ukun.api.stores.application.StoreResponse;
import java.util.List;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;

/** {@code GET /api/stores} (RF-02, {@code listStores} of the contract). */
class StoreCatalogIT extends StoresIntegrationTest {

    private static final String STORES = "/api/stores";

    private static final String ONLINE = "Ukun Online";
    private static final String ALCALA = "Ukun Alcalá de Henares";
    private static final String TORREJON = "Ukun Torrejón de Ardoz";
    private static final String BARCELONA = "Ukun Barcelona";

    /**
     * Alphabetical, and not the insertion order the fixture uses — which starts with the online store,
     * the one that sorts last. With a fixture already in order this test would pass against a listing
     * sorted by nothing at all.
     */
    @Test
    @DisplayName("every store comes back sorted by name, not in insertion order")
    void listsEveryStoreSortedByName() {
        assertThat(namesOf(storesOf(STORES))).containsExactly(ALCALA, BARCELONA, ONLINE, TORREJON);
    }

    /**
     * The full shape of one row: the four address fields nested, the phone in E.164, and the flag.
     * {@code isOnline} is asserted as a JSON path and not only through the deserialised record because
     * that name survives two renamings on the way out — Lombok's getter drops the {@code is} prefix and
     * so would Jackson's bean naming, and the contract asks for {@code isOnline}.
     */
    @Test
    @DisplayName("a store carries its address, its phone and the online flag under the contract's names")
    void exposesTheContractShapeOfAStore() {
        restTestClient
                .get()
                .uri(STORES)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[2].name")
                .isEqualTo(ONLINE)
                .jsonPath("$[2].isOnline")
                .isEqualTo(true)
                .jsonPath("$[2].phone")
                .isEqualTo("+34910000001")
                .jsonPath("$[2].address.street")
                .isEqualTo("Calle Mayor, 1")
                .jsonPath("$[2].address.city")
                .isEqualTo("Madrid")
                .jsonPath("$[2].address.province")
                .isEqualTo("Madrid")
                .jsonPath("$[2].address.postalCode")
                .isEqualTo("28013")
                .jsonPath("$[0].isOnline")
                .isEqualTo(false);
    }

    /**
     * Exactly one, and it is the database that guarantees it ({@code ux_store_online}, a partial unique
     * index). The assertion is here rather than in a schema test because this is the endpoint whose
     * caller depends on it: the SPA picks the store that serves orders out of this array.
     */
    @Test
    @DisplayName("exactly one store is flagged as the online one")
    void marksOnlyOneStoreAsOnline() {
        assertThat(storesOf(STORES))
                .filteredOn(StoreResponse::isOnline)
                .extracting(StoreResponse::name)
                .containsExactly(ONLINE);
    }

    /** A flat array, as the contract declares for closed reference data — no pagination wrapper. */
    @Test
    @DisplayName("the response is a bare array, with no pagination wrapper")
    void returnsAFlatArray() {
        restTestClient
                .get()
                .uri(STORES)
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

    @Test
    @DisplayName("q matches a store by name")
    void filtersByName() {
        assertThat(namesOf(storesOf(STORES + "?q=Barcelona"))).containsExactly(BARCELONA);
    }

    /**
     * "Barcelona" is both a name fragment and a city, and it returns one store rather than two rows for
     * the same one: the two predicates are {@code OR}ed inside a single query, not two queries stitched
     * together.
     */
    @Test
    @DisplayName("q matching both the name and the city of one store does not duplicate it")
    void doesNotDuplicateAStoreMatchedTwice() {
        assertThat(storesOf(STORES + "?q=Barcelona")).hasSize(1);
    }

    /**
     * Defect #22, the reason this endpoint reuses the catalogue's folding. Nobody types
     * {@code Torrejón} with the accent, and without {@code unaccent()} the store would be
     * unreachable by the only spelling a customer produces.
     */
    @Test
    @DisplayName("q ignores accents, so 'torrejon' finds the store in Torrejón de Ardoz")
    void filtersByCityIgnoringAccents() {
        assertThat(namesOf(storesOf(STORES + "?q=torrejon"))).containsExactly(TORREJON);
    }

    /**
     * The same folding in the other direction: an accent typed in still matches. The term goes through
     * a URI template variable so the client percent-encodes it — spelling {@code %C3%A1} by hand into
     * the string would be encoded a second time and search for the escape sequence itself.
     */
    @Test
    @DisplayName("q ignores accents typed in as well, so 'Alcalá' finds the store")
    void filtersByAccentedTermToo() {
        assertThat(namesOf(storesOf(STORES + "?q={q}", "Alcalá"))).containsExactly(ALCALA);
    }

    @Test
    @DisplayName("q matching nothing returns an empty array, not an error")
    void returnsAnEmptyArrayWhenNothingMatches() {
        assertThat(storesOf(STORES + "?q=Vigo")).isEmpty();
    }

    /**
     * A {@code %} typed into the box is a character, not a wildcard. Without escaping it, this search
     * would return the whole network — the failure mode is a filter that silently stops filtering.
     */
    @Test
    @DisplayName("a wildcard character in q is searched for literally")
    void treatsWildcardsInTheTermAsLiterals() {
        assertThat(storesOf(STORES + "?q=%25")).isEmpty();
    }

    /** {@code minLength: 1} in the contract: an empty {@code q} is a rejected request, not "everything". */
    @Test
    @DisplayName("an empty q is rejected as a validation failure")
    void rejectsAnEmptyTerm() {
        restTestClient
                .get()
                .uri(STORES + "?q=")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VALIDATION_FAILED");
    }

    /**
     * The search term is a bind parameter, not text spliced into the statement. Asserted on the SQL
     * itself rather than on the result, because the result is the same either way — that is exactly
     * what makes the inlined version hard to notice.
     *
     * <p>It guards {@code TextSearch}, so it covers {@code /api/products} and its typeahead too: the
     * three of them go through the same helper, and the typeahead is the one that fires on every
     * keystroke.
     *
     * <p>Two things would break it. Going back to {@code CriteriaBuilder.literal()} puts the term in
     * the SQL text, which mints one statement per distinct search and defeats both the plan cache and
     * the prepared-statement cache. And the injection attempt below survives today only because
     * Hibernate doubles quotes as it renders — a layer below anything this codebase controls. A bound
     * parameter makes both a property of the query instead of a behaviour of the renderer.
     */
    @Test
    @DisplayName("the search term is bound as a parameter and never reaches the SQL text")
    void bindsTheSearchTermInsteadOfInliningIt() {
        Statistics statistics = freshStatistics();

        assertThat(storesOf(STORES + "?q={q}", "' OR 1=1 --")).isEmpty();

        assertThat(statistics.getQueries())
                .singleElement(as(STRING))
                .contains("like unaccent(lower(?))")
                .doesNotContain("OR 1=1");
    }

    /**
     * Defect #12. Four stores, four addresses, and one statement: the {@code @EntityGraph} fetches the
     * association in the same query. Without it this would be five, and the number would grow with the
     * network — the exact shape ADR-0017 §2 promises stays fixed because the crossing is intra-module.
     */
    @Test
    @DisplayName("the listing fetches every address in one statement, with no N+1")
    void fetchesAddressesWithoutAnNPlusOne() {
        Statistics statistics = freshStatistics();

        assertThat(storesOf(STORES)).hasSize(4);

        assertThat(statistics.getPrepareStatementCount()).isOne();
    }

    /**
     * The endpoint keeps working when the published document drifts, which is what makes the drift
     * silent — the same failure mode as {@code 33d6f19}, where two bound objects were published as two
     * opaque parameters. {@code minLength} is the one asserted in detail: written as
     * {@code @NotBlank} instead of {@code @Size(min = 1, …)} the endpoint would behave identically and
     * publish {@code minLength: 0}.
     */
    @Test
    @DisplayName("the generated OpenAPI declares listStores with the query parameter of the contract")
    void publishesTheContractParameters() {
        String parameter = "$.paths['/api/stores'].get.parameters[?(@.name=='q')]";

        restTestClient
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.paths['/api/stores'].get.operationId")
                .isEqualTo("listStores")
                .jsonPath(parameter + ".required")
                .isEqualTo(false)
                .jsonPath(parameter + ".schema.minLength")
                .isEqualTo(1)
                .jsonPath(parameter + ".schema.maxLength")
                .isEqualTo(255);
    }

    private List<StoreResponse> storesOf(String uri, Object... uriVariables) {
        return restTestClient
                .get()
                .uri(uri, uriVariables)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(new ParameterizedTypeReference<List<StoreResponse>>() {})
                .returnResult()
                .getResponseBody();
    }

    private List<String> namesOf(List<StoreResponse> stores) {
        return stores.stream().map(StoreResponse::name).toList();
    }
}
