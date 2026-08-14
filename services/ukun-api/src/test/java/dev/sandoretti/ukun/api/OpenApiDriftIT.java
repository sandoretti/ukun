package dev.sandoretti.ukun.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The drift check of {@code docs/04-roadmap.md} ("Artefactos-contrato", point 3) and
 * {@code docs/03-analisis-y-patrones.md} §3: it starts the application, reads the {@code /v3/api-docs}
 * springdoc generates from the running code, and compares it against the frozen contract in
 * {@code docs/api/openapi/ukun-api.yaml}. Contract testing without bringing in Pact.
 *
 * <p><strong>The YAML is the source of truth and this test never questions it.</strong> It is
 * hand-written design-first and frozen since SINC-1; springdoc emits {@code /v3/api-docs} for exactly
 * one reason, which its own header states: so this comparison can be made. A red build here therefore
 * means the code moved away from the contract — the fix belongs in the code, never in the YAML.
 *
 * <p>It runs as an {@code *IT} rather than a task of its own so that
 * {@code ./gradlew :ukun-api:integrationTest} — what the CI of v1.0 will invoke
 * ({@code docs/13-testing.md} §3) — already covers it with no extra wiring.
 *
 * <h2>What is compared</h2>
 *
 * The <em>shape</em> of the contract, never its text: which operations exist, under which
 * {@code operationId} and which security requirement, their parameters with {@code in} /
 * {@code required} / {@code style} / {@code explode} and schema, the set of response status codes with
 * their media types, and — for a success — the whole response body resolved property by property:
 * names, which of them are required, their types (nullability included), formats and enums.
 * {@link OpenApiShape} explains how both documents are projected onto that.
 *
 * <p>The rule of thumb behind the list: <strong>everything that changes the type the frontend
 * generates from the contract</strong> with {@code openapi-typescript} (ADR-0019). If the SPA would
 * compile against a payload the service does not send, this test has to be red.
 *
 * <h2>What is not compared, and why</h2>
 *
 * Every exclusion is a permanent hole in the check, so there are four and each one is here because
 * closing it would compare something that is not the contract:
 *
 * <ol>
 *   <li><strong>Prose and examples</strong> ({@code summary}, {@code description}, {@code tags},
 *       {@code example}/{@code examples}), plus {@code info} and {@code servers}. The contract is
 *       hand-written precisely so it can explain itself; requiring springdoc to reproduce that prose
 *       would mean copying every paragraph into a Java annotation, which is a second source of truth
 *       for the text and the very thing ADR-0009 rejects elsewhere.
 *   <li><strong>Validation keywords</strong> ({@code pattern}, {@code minLength}/{@code maxLength},
 *       {@code minimum}/{@code maximum}, {@code minItems}/{@code maxItems}, {@code default}). They
 *       constrain a value, they do not change its shape: no generated client type changes when a
 *       {@code maxLength} does. They are enforced by Bean Validation and asserted against real
 *       requests by the endpoint tests, which is a stronger check than a document stating them.
 *   <li><strong>The name of each component</strong> — {@code $ref}s are resolved before comparing.
 *       See {@link OpenApiShape}: springdoc names schemas after the Java class, the contract names
 *       them for the client, and no client is generated from {@code /v3/api-docs}.
 *   <li><strong>The body of an error response.</strong> Its status code and its
 *       {@code application/problem+json} media type are compared; its schema is not. It is one
 *       cross-cutting shape owned by {@code docs/12-error-contract.md}, produced by a
 *       {@code @RestControllerAdvice} that springdoc cannot introspect, and asserted field by field
 *       ({@code code}, {@code traceId}, {@code errors[]}) against real error responses in the endpoint
 *       tests. Publishing it faithfully — the closed {@code ErrorCode} registry included — is worth
 *       doing, and worth its own issue.
 * </ol>
 */
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // springdoc is off by default and only on under the `dev` profile (application.yaml). It is
        // turned on by property rather than by activating that profile, which would also switch
        // Liquibase to the `demo` context and seed a catalogue this test has no use for.
        properties = {"spring.liquibase.contexts=prod", "springdoc.api-docs.enabled=true"})
class OpenApiDriftIT extends AbstractIntegrationTest {

    private static final Path CONTRACT = repositoryRoot().resolve("docs/api/openapi/ukun-api.yaml");

    /**
     * Operations the contract declares and no release has implemented yet. The contract is written
     * design-first and covers the whole system up to v0.7, so without this list the check could only
     * be introduced in the last release — and the roadmap put it in v0.3, when the catalogue is the
     * only thing serving requests.
     *
     * <p>It is an explicit list and not "whatever is missing" on purpose: that is what makes an
     * endpoint <em>disappearing</em> a failure rather than a silence. Implementing one of these means
     * deleting its line here, and the day the list is empty the check is total.
     *
     * <p><strong>It is empty, and that day is today</strong> (issue #79, the SKU look-up of the stock
     * slice — the last one left after #43 took the product and product-type CRUD and #45 the two staff
     * operations). The check now compares the whole contract against the whole implementation, so it
     * stays: an operation added to the YAML before its code lands has somewhere to wait, and the
     * {@code isSubsetOf} below keeps that waiting from being silent.
     */
    private static final Set<String> NOT_IMPLEMENTED_YET = Set.of();

    /** The frozen contract, read and projected once: it is a file, and it does not change mid-run. */
    private static OpenApiShape contract;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, SCHEMA_ONLY_DATABASE);
    }

    @Autowired
    private RestTestClient restTestClient;

    @BeforeAll
    static void readTheContract() {
        try {
            contract = OpenApiShape.of(new ObjectMapper(new YAMLFactory()).readTree(Files.readString(CONTRACT)));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the frozen contract " + CONTRACT, e);
        }
    }

    /**
     * An empty diff proves nothing if the two documents speak different dialects: the same operation
     * declares nullability as {@code type: [string, 'null']} in 3.1 and as {@code nullable: true} in
     * 3.0, and a projection of the 3.0 spelling against a 3.1 contract would compare silence with
     * silence.
     */
    @Test
    @DisplayName("both documents are OpenAPI 3.1")
    void publishesTheOpenApiVersionOfTheContract() {
        assertThat(published().version())
                .as("the OpenAPI version springdoc emits")
                .isEqualTo(contract.version())
                .startsWith("3.1");
    }

    @Test
    @DisplayName("every operation of the contract that is implemented is published, and no other")
    void publishesExactlyTheImplementedOperationsOfTheContract() {
        Map<String, String> declared = contract.operationIds();
        Map<String, String> published = published().operationIds();

        Set<String> expected = new TreeSet<>(declared.entrySet().stream()
                .filter(operation -> !NOT_IMPLEMENTED_YET.contains(operation.getValue()))
                .map(Map.Entry::getKey)
                .toList());

        assertThat(new TreeSet<>(published.keySet()))
                .as(
                        """
                        the operations ukun-api publishes, against the ones %s declares \
                        minus the %d not implemented yet. An operation only in the contract is either \
                        an endpoint that was deleted or one that has to be listed in NOT_IMPLEMENTED_YET; \
                        one only in the implementation is an endpoint the contract does not declare\
                        """,
                        CONTRACT.getFileName(), NOT_IMPLEMENTED_YET.size())
                .isEqualTo(expected);
    }

    /**
     * Guards the list above against rotting: a name that no longer exists in the contract would sit
     * there excusing an operation nobody is waiting for, and would go on excusing it after the contract
     * renamed it.
     */
    @Test
    @DisplayName("the not-implemented list only names operations the contract declares")
    void namesOnlyContractOperationsAsPending() {
        assertThat(NOT_IMPLEMENTED_YET)
                .as("operationIds listed as not implemented yet")
                .isSubsetOf(contract.operationIds().values());
    }

    @Test
    @DisplayName("the published document has the shape the frozen contract declares")
    void publishesTheContractShapeOfEveryImplementedOperation() {
        List<String> divergences = divergences(contract, published());

        assertThat(divergences)
                .as(
                        """
                        the shape ukun-api publishes at /v3/api-docs, against the frozen contract %s. \
                        The YAML is the source of truth: fix the code, never the contract\
                        """,
                        CONTRACT)
                .isEmpty();
    }

    /**
     * One line per facet that differs, restricted to the operations both documents describe: which ones
     * exist at all is the previous test's job, and reporting a whole unimplemented operation facet by
     * facet here would bury the divergences that matter.
     */
    private static List<String> divergences(OpenApiShape declared, OpenApiShape published) {
        Set<String> operations = new TreeSet<>(published.operationIds().keySet());
        operations.retainAll(declared.operationIds().keySet());

        Set<String> coordinates = new LinkedHashSet<>(declared.facets().keySet());
        coordinates.addAll(published.facets().keySet());

        List<String> divergences = new ArrayList<>();
        coordinates.stream()
                .filter(coordinate -> operations.contains(operationOf(coordinate)))
                .forEach(coordinate -> {
                    String expected = declared.facets().get(coordinate);
                    String actual = published.facets().get(coordinate);
                    if (!Objects.equals(expected, actual)) {
                        divergences.add("%s — contract: %s, published: %s"
                                .formatted(coordinate, orAbsent(expected), orAbsent(actual)));
                    }
                });

        return divergences;
    }

    /** Every coordinate opens with the operation it belongs to, up to the first separator. */
    private static String operationOf(String coordinate) {
        return coordinate.substring(0, coordinate.indexOf(OpenApiShape.SEPARATOR));
    }

    private static String orAbsent(String value) {
        return value == null ? "(not declared)" : value;
    }

    /** The document the running application generates, read straight off the endpoint CI will read. */
    private OpenApiShape published() {
        byte[] body = restTestClient
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .returnResult()
                .getResponseBody();

        try {
            return OpenApiShape.of(new ObjectMapper().readTree(body));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the document published at /v3/api-docs", e);
        }
    }

    /**
     * The contract lives in {@code docs/}, outside this Gradle project, so the path is resolved from the
     * repository root instead of from a working directory that Gradle and an IDE set differently.
     */
    private static Path repositoryRoot() {
        Path directory = Path.of("").toAbsolutePath();
        while (!Files.exists(directory.resolve("settings.gradle.kts"))) {
            directory = directory.getParent();
            if (directory == null) {
                throw new IllegalStateException("No settings.gradle.kts above " + Path.of("").toAbsolutePath());
            }
        }

        return directory;
    }
}
