package dev.sandoretti.ukun.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Day-one stack validation required by docs/05-stack-y-versiones.md (Lombok row): prove that
 * Lombok, MapStruct, Testcontainers and springdoc all work on JDK 25 with Spring Boot 4.1
 * <em>before</em> anything is built on top of them.
 *
 * <p>It is kept as a permanent test rather than a throwaway probe because the risk it covers is
 * recurring: every JDK or Boot bump can break an annotation processor, and this fails the build
 * the moment that happens instead of at the first mapper someone writes.
 *
 * <p>If Lombok ever stops working here, docs/05 has already decided the outcome: drop Lombok in
 * favour of records and explicit accessors — do not work around it.
 */
@ActiveProfiles("dev")
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StackValidationIT extends AbstractIntegrationTest {

    /**
     * A datasource became mandatory here the moment the ukun_app baseline added JPA and Liquibase:
     * with one on the classpath and no URL, the context no longer starts at all.
     *
     * <p>It is the seeded database because the {@code dev} profile this class needs for springdoc
     * also sets {@code spring.liquibase.contexts=demo} in {@code application.yaml} — the two travel
     * together, so it shares a database with {@link DemoDataIT} rather than migrating a third one.
     */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, SEEDED_DATABASE);
    }

    @Autowired
    private ProbeMapper mapper;

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void lombokGeneratesAccessors() {
        Probe probe = new Probe();
        probe.setName("widget");
        probe.setQuantity(3);

        assertThat(probe.getName()).isEqualTo("widget");
        assertThat(probe.getQuantity()).isEqualTo(3);
    }

    @Test
    void mapstructReadsLombokAccessorsAndIsASpringBean() {
        Probe probe = new Probe();
        probe.setName("widget");
        probe.setQuantity(3);

        ProbeView view = mapper.toView(probe);

        // A null name here would mean MapStruct ran before Lombok and generated an empty
        // mapper — the exact failure the processor order in the root pom.xml prevents.
        assertThat(view).isEqualTo(new ProbeView("widget", 3));
    }

    @Test
    void testcontainersStartsPostgres() {
        assertThat(postgres().isRunning()).isTrue();
        assertThat(jdbcUrlOf(SEEDED_DATABASE)).startsWith("jdbc:postgresql://");
    }

    @Test
    void springdocServesTheOpenApiDocument() {
        String document = restTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(document).contains("\"openapi\"");
    }
}
