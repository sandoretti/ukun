package dev.sandoretti.ukun.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * What Actuator answers to a caller with no credentials, and what it must not answer.
 *
 * <p>The configuration has said {@code include: health} since v0.2, with the comment «anything else is
 * opt-in per release, never by default» — and until v1.0 nothing checked it. That is a gap worth
 * closing precisely because the way it breaks is by addition: exposing one more endpoint to debug
 * something is a one-word edit, and {@code /actuator/env} hands out every resolved property, which in
 * this system includes the datasource URL and the two OAuth2 client secrets.
 *
 * <p>Here the refusal takes a third shape, and it is worth knowing before reading the assertion: this
 * service has a login form, so its security chain answers an unauthorised request with a 302 to
 * {@code /login} rather than with a 401. A redirect to the form is a refusal like any other — but only
 * if it really goes to the form, so that branch checks where it points instead of taking any 302 as
 * good news. What all three shapes have in common is the only answer that would be a defect: 200.
 */
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorExposureIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, SCHEMA_ONLY_DATABASE);
    }

    @Autowired
    private RestTestClient restTestClient;

    @Test
    @DisplayName("answers health, which is what the container's HEALTHCHECK polls")
    void answersHealth() {
        restTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/metrics",
        "/actuator/heapdump", "/actuator/loggers", "/actuator/mappings"})
    @DisplayName("does not answer anything else")
    void doesNotAnswerAnythingElse(String endpoint) {
        var response = restTestClient.get().uri(endpoint).exchange().returnResult(Void.class);
        HttpStatus status = HttpStatus.valueOf(response.getStatus().value());

        if (status == HttpStatus.FOUND) {
            assertThat(response.getResponseHeaders().getLocation())
                    .as("a redirect is only a refusal if it goes to the login form")
                    .isNotNull()
                    .extracting(URI::getPath)
                    .isEqualTo("/login");
        } else {
            assertThat(status).isIn(HttpStatus.NOT_FOUND, HttpStatus.UNAUTHORIZED);
        }
    }
}
