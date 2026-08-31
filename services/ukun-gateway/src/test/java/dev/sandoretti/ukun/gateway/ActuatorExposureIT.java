package dev.sandoretti.ukun.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

/**
 * What Actuator answers to a caller with no credentials, and what it must not answer.
 *
 * <p>The configuration has said {@code include: health} since v0.2, with the comment «anything else is
 * opt-in per release, never by default» — and until v1.0 nothing checked it. That is a gap worth
 * closing precisely because the way it breaks is by addition: exposing one more endpoint to debug
 * something is a one-word edit, and {@code /actuator/env} hands out every resolved property, which in
 * this system includes the datasource URL and the two OAuth2 client secrets.
 *
 * <p>The assertion accepts both 404 and 401 on purpose. Which of the two comes back depends on whether
 * the security chain rejects the request before the missing endpoint is noticed, and that is an
 * implementation detail of an ordering this test has no business pinning. What it does pin is the only
 * answer that would be a defect: 200.
 */
class ActuatorExposureIT extends GatewayIntegrationTest {

    @Test
    @DisplayName("answers health, which is what the container's HEALTHCHECK polls")
    void answersHealth() {
        http.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");
    }

    /**
     * Here the endpoint being absent matters twice: {@code /actuator} is a reserved prefix
     * ({@code ReservedPaths}), so a path that no endpoint claims must not be swallowed by the SPA
     * fallback either — an {@code index.html} with status 200 would pass a laxer assertion.
     */
    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/metrics",
        "/actuator/heapdump", "/actuator/loggers", "/actuator/mappings"})
    @DisplayName("does not answer anything else, and does not fall back to the SPA")
    void doesNotAnswerAnythingElse(String endpoint) {
        HttpStatus status = HttpStatus.valueOf(http.get().uri(endpoint)
                .exchange()
                .expectBody(String.class)
                .returnResult()
                .getStatus()
                .value());

        assertThat(status).isIn(HttpStatus.NOT_FOUND, HttpStatus.UNAUTHORIZED);
    }
}
