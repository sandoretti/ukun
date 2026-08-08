package dev.sandoretti.ukun.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import dev.sandoretti.ukun.api.AbstractIntegrationTest;
import dev.sandoretti.ukun.api.catalog.domain.ProductType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code GET /api/products} against a real Postgres (RF-01, {@code listProducts} of the contract).
 * Testcontainers rather than H2, per the {@code testing} skill, and here that is not ceremony: half
 * of these tests are about {@code unaccent()}, a Postgres extension H2 does not have.
 *
 * <p>It seeds its own rows with {@link ProductMother} into {@link #CATALOG_DATABASE}, so the two
 * defects under test are built explicitly instead of being fished out of the demo catalogue — and so
 * nothing it writes is visible to the tests that count the seed data.
 */
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.liquibase.contexts=prod",
            "spring.jpa.properties.hibernate.generate_statistics=true",
            "springdoc.api-docs.enabled=true"
        })
class ProductCatalogIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, CATALOG_DATABASE);
    }

    private static final String CATALOG = "/api/products";

    /**
     * The response as the frontend will generate it from the contract. Reading it back into a record
     * is what makes a renamed field fail here rather than in the browser — and {@code price} is
     * declared {@code String} because the {@code Money} schema says so.
     */
    private record ProductPage(List<Item> items, int page, int size, long totalElements, int totalPages) {}

    private record Item(
            Long id, String sku, String name, String price, Long typeId, String typeName, String imageUrl) {}

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * Persisted in a transaction of its own that commits before any request is made: the endpoint is
     * served on another thread, so rows left inside an open test transaction would be invisible to it.
     */
    @BeforeEach
    void seedTheCatalogue() {
        ProductType sofas = ProductMother.type("Sofás");
        ProductType beds = ProductMother.type("Camas");
        ProductType tables = ProductMother.type("Mesas");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            List.of(sofas, beds, tables).forEach(entityManager::persist);
            List.of(
                            ProductMother.leatherSofa(sofas),
                            ProductMother.bunkBed(beds),
                            ProductMother.coffeeTable(tables),
                            ProductMother.floorLamp(tables),
                            ProductMother.discontinuedChair(tables))
                    .forEach(entityManager::persist);
        });
    }

    /** Ordered truncation rather than {@code @DirtiesContext}, per the `testing` skill. */
    @AfterEach
    void clearTheCatalogue() {
        jdbc.execute("TRUNCATE TABLE product, product_type RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("the public catalogue never returns a discontinued product")
    void returnsOnlyActiveProducts() {
        List<String> names = namesOf(CATALOG + "?size=100");

        assertThat(names).doesNotContain("Silla retirada").hasSize(4);
    }

    /**
     * Defect #22, in the three shapes it takes in the seed catalogue. The customer types no accents
     * and the catalogue is full of them, so {@code unaccent(lower(…))} is applied on both sides of
     * the comparison — normalising only the stored name would leave {@code "café"} unreachable.
     */
    @Test
    @DisplayName("a search without accents finds the accented names")
    void findsAccentedNamesFromAnUnaccentedTerm() {
        assertSoftly(softly -> {
            softly.assertThat(namesOf(CATALOG + "?name=cafe")).containsExactly("Mesa de café");
            softly.assertThat(namesOf(CATALOG + "?name=sofa")).containsExactly("Sofá de cuero");
            softly.assertThat(namesOf(CATALOG + "?name=lampara")).containsExactly("Lámpara de pie");
        });
    }

    @Test
    @DisplayName("the search ignores case and matches anywhere in the name")
    void findsProductsByAnySubstringOfTheName() {
        assertThat(namesOf(CATALOG + "?name=CUERO")).containsExactly("Sofá de cuero");
    }

    @Test
    @DisplayName("filtering by type returns only that type's products")
    void filtersByType() {
        List<String> names = namesOf(CATALOG + "?typeId=" + typeIdOf("Mesas") + "&size=100");

        assertThat(names).containsExactlyInAnyOrder("Mesa de café", "Lámpara de pie");
    }

    @Test
    @DisplayName("the price range includes both of its bounds")
    void filtersByPriceRangeInclusively() {
        assertSoftly(softly -> {
            softly.assertThat(namesOf(CATALOG + "?priceMin=49.90&priceMax=129.75&size=100"))
                    .containsExactlyInAnyOrder("Lámpara de pie", "Mesa de café");
            softly.assertThat(namesOf(CATALOG + "?priceMin=399.99&size=100"))
                    .containsExactlyInAnyOrder("Sofá de cuero", "Litera infantil");
        });
    }

    @Test
    @DisplayName("the filters compose instead of overriding each other")
    void appliesEveryFilterAtOnce() {
        List<String> names = namesOf(CATALOG + "?name=a&typeId=" + typeIdOf("Mesas") + "&priceMin=100&size=100");

        assertThat(names).containsExactly("Mesa de café");
    }

    @Test
    @DisplayName("PRICE_DESC sorts from the most expensive down")
    void sortsByPriceDescending() {
        List<String> prices =
                pageOf(CATALOG + "?sort=PRICE_DESC&size=100").items().stream().map(Item::price).toList();

        assertThat(prices).containsExactly("399.99", "399.99", "129.75", "49.90");
    }

    /**
     * Defect #23, and the reason every {@code Sort} of this API ends in {@code id}. Two products cost
     * {@code 399.99}, so without a tie-breaker Postgres is free to order them differently for each
     * page and a row can come back twice — or never. Page size one is the harshest way to ask.
     */
    @Test
    @DisplayName("paginating through a price tie neither repeats nor loses a row")
    void paginatesStablyAcrossAPriceTie() {
        List<Long> wholeCatalogue = idsOf(CATALOG + "?size=100");

        List<Long> pagedThrough = new ArrayList<>();
        for (int page = 0; page < wholeCatalogue.size(); page++) {
            pagedThrough.addAll(idsOf(CATALOG + "?size=1&page=" + page));
        }

        assertThat(pagedThrough).doesNotHaveDuplicates().containsExactlyElementsOf(wholeCatalogue);
    }

    @Test
    @DisplayName("the page wrapper is the one of the contract, not Spring's serialisation of Page")
    void exposesTheAgreedPaginationShape() {
        restTestClient
                .get()
                .uri(CATALOG + "?size=3")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.page")
                .isEqualTo(0)
                .jsonPath("$.size")
                .isEqualTo(3)
                .jsonPath("$.totalElements")
                .isEqualTo(4)
                .jsonPath("$.totalPages")
                .isEqualTo(2)
                .jsonPath("$.content")
                .doesNotExist()
                .jsonPath("$.pageable")
                .doesNotExist();
    }

    /**
     * The shape of one row, asserted on the raw JSON. {@code price} is compared against a
     * <em>string</em> on purpose: the {@code Money} schema forbids a JSON number, because that is how
     * the legacy lost cents to {@code Float} (defect #5), and this assertion fails if it ever comes
     * back as one.
     *
     * <p>{@code available} must not be there either: availability is a separate read, since
     * {@code catalog} cannot depend on {@code inventory} (ADR-0017 §3). Nor may {@code imageKey} —
     * the storage key is internal ({@code docs/08} §9).
     */
    @Test
    @DisplayName("a row carries its type, a servable image URL and a decimal price as text")
    void exposesTheContractShapeOfAProduct() {
        restTestClient
                .get()
                .uri(CATALOG + "?name=cuero")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.items[0].sku")
                .isEqualTo("SOFA-CUERO")
                .jsonPath("$.items[0].typeName")
                .isEqualTo("Sofás")
                .jsonPath("$.items[0].price")
                .isEqualTo("399.99")
                .jsonPath("$.items[0].imageUrl")
                .isEqualTo("/media/products/sofa-cuero.jpg")
                .jsonPath("$.items[0].available")
                .doesNotExist()
                .jsonPath("$.items[0].imageKey")
                .doesNotExist();
    }

    @Test
    @DisplayName("a product without a photo reports a null imageUrl, not a URL pointing nowhere")
    void reportsANullImageUrlWhenThereIsNoPhoto() {
        Item bunkBed = pageOf(CATALOG + "?name=litera").items().getFirst();

        assertThat(bunkBed.imageUrl()).isNull();
    }

    /**
     * The mechanical N+1 check the {@code testing} skill requires of every listing. Without the
     * {@code @EntityGraph}, resolving {@code typeName} would fire one {@code SELECT} per row — defect
     * #12, invisible with four products and fatal with four thousand.
     *
     * <p>Exactly two statements, the page and its {@code count}, not "at most a few": an exact number
     * is what makes a fetch strategy quietly changing tomorrow fail the build.
     *
     * <p>The page size is deliberately smaller than the catalogue. Spring Data skips the {@code count}
     * altogether when the first page comes back short, so asking for all four products at once would
     * measure one statement and never exercise the second — and the real catalogue has a hundred rows
     * and more than one page.
     *
     * <p>{@code typeName} is asserted alongside the count because it is what makes the count mean
     * anything: if nothing read the association, there would be no N+1 to detect in the first place.
     */
    @Test
    @DisplayName("the listing resolves every product type in the same query")
    void listsTheCatalogueWithoutAnNPlusOne() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        List<Item> firstPage = pageOf(CATALOG + "?size=2").items();

        assertThat(firstPage).hasSize(2).extracting(Item::typeName).doesNotContainNull();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    /**
     * The other half of the plumbing this slice introduces: a rejected request comes back as the
     * {@code ProblemDetail} of {@code docs/12-error-contract.md} §1, not Spring's default error body.
     * {@code code} is what the frontend switches on, {@code traceId} is in <em>every</em> error
     * response, and {@code errors[]} is what gets painted under the field.
     */
    @Test
    @DisplayName("an out-of-range page size is a ProblemDetail with code VALIDATION_FAILED")
    void rejectsAnOutOfRangePageSize() {
        restTestClient
                .get()
                .uri(CATALOG + "?size=101")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.type")
                .isEqualTo("https://ukun.dev/errors/validation-failed")
                .jsonPath("$.status")
                .isEqualTo(400)
                .jsonPath("$.traceId")
                .isNotEmpty()
                .jsonPath("$.errors[0].field")
                .isEqualTo("size");
    }

    /**
     * A route that does not exist must be a {@code 404}, and this test exists because it was not: a
     * catch-all {@code @ExceptionHandler(Exception.class)} used to swallow the exceptions Spring MVC
     * already classifies and answer {@code 500 INTERNAL_ERROR}. Every route scan became a logged
     * stack trace and an alert.
     */
    @Test
    @DisplayName("an unknown route is a 404, not a 500")
    void reportsAnUnknownRouteAsNotFound() {
        restTestClient
                .get()
                .uri("/api/does-not-exist")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("RESOURCE_NOT_FOUND")
                .jsonPath("$.traceId")
                .isNotEmpty();
    }

    /** Same defect, the other half: a write verb on a read-only endpoint is a {@code 405}. */
    @Test
    @DisplayName("a method the endpoint does not support is a 405, not a 500")
    void reportsAnUnsupportedMethodAsMethodNotAllowed() {
        restTestClient
                .post()
                .uri(CATALOG)
                .exchange()
                .expectStatus()
                .isEqualTo(405)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("METHOD_NOT_ALLOWED")
                .jsonPath("$.traceId")
                .isNotEmpty();
    }

    @Test
    @DisplayName("a filter of the wrong type is reported as a field error, never as a 500")
    void rejectsAFilterOfTheWrongType() {
        restTestClient
                .get()
                .uri(CATALOG + "?priceMin=cheap")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors[0].field")
                .isEqualTo("priceMin");
    }

    /**
     * The generated document has to describe the same seven query parameters the frozen contract
     * declares. It did not: binding the filters as objects made springdoc publish two opaque
     * parameters named {@code filters} and {@code pagination}, while the endpoint kept answering
     * correctly — a drift nothing would have caught until CI diffed the two documents, or until the
     * frontend generated its client from the wrong one.
     *
     * <p>The class turns {@code springdoc.api-docs} on for this, rather than activating the
     * {@code dev} profile: the profile would also switch Liquibase to the {@code demo} context and
     * seed a hundred products into the database every other test here counts.
     */
    @Test
    @DisplayName("the generated OpenAPI declares the query parameters of the contract")
    void publishesTheContractParameters() {
        restTestClient
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.paths['/api/products'].get.operationId")
                .isEqualTo("listProducts")
                .jsonPath("$.paths['/api/products'].get.parameters[*].name")
                .value(names -> assertThat((List<String>) names)
                        .containsExactlyInAnyOrder("name", "typeId", "priceMin", "priceMax", "sort", "page", "size"));
    }

    private ProductPage pageOf(String uri) {
        return restTestClient
                .get()
                .uri(uri)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(ProductPage.class)
                .returnResult()
                .getResponseBody();
    }

    private List<String> namesOf(String uri) {
        return pageOf(uri).items().stream().map(Item::name).toList();
    }

    private List<Long> idsOf(String uri) {
        return pageOf(uri).items().stream().map(Item::id).toList();
    }

    private long typeIdOf(String name) {
        return jdbc.queryForObject("SELECT id FROM product_type WHERE name = ?", Long.class, name);
    }
}
