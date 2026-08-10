package dev.sandoretti.ukun.api.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.api.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * This service's end of issue #30: the {@code traceId} it reports is the one the gateway sent, not one
 * of its own invention.
 *
 * <p>Asserted through the {@code ProblemDetail} because that is where the value is observable from
 * outside, and because it is the same value {@code GlobalExceptionHandler} logs the exception with —
 * {@code shared/error/TraceIds} reads the MDC that Micrometer Tracing fills, and both ends come from
 * that single read. So this test covers two facts at once: the incoming {@code traceparent} reaches
 * the MDC, and the error contract stopped handing out identifiers that correlate with nothing.
 *
 * <p>Before this release {@code TraceIds} fell back to a fresh random value on every call, and it
 * still does whenever there is no span. That fallback is why the assertion has to compare against the
 * <em>given</em> trace rather than merely check the field is non-empty: a test that only asserted
 * {@code isNotEmpty()} passed for the whole of v0.3 and v0.4, and would keep passing if tracing were
 * removed tomorrow.
 *
 * <h2>A context of its own</h2>
 *
 * One more application context is a real cost in this suite, and it is taken deliberately: the fact
 * under test is about the whole service rather than about any module, so hanging it off the catalogue
 * or the profile fixture would put it in a package that does not own it — and both of those fixtures
 * seed and truncate tables this test has no use for. It talks to the schema-only database and writes
 * nothing.
 */
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TraceCorrelationIT extends AbstractIntegrationTest {

    private static final String TRACEPARENT = "traceparent";

    /** Public, needs no token, and answers a {@code ProblemDetail} without touching a row. */
    private static final String UNKNOWN_PRODUCT = "/api/products/999999";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, SCHEMA_ONLY_DATABASE);
    }

    @Autowired
    private RestTestClient restTestClient;

    @Test
    @DisplayName("an error reports the trace id the caller arrived with")
    void reportsTheIncomingTraceId() {
        String traceId = aTraceId();

        restTestClient
                .get()
                .uri(UNKNOWN_PRODUCT)
                .header(TRACEPARENT, "00-%s-%s-01".formatted(traceId, aTraceId().substring(0, 16)))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.traceId")
                .isEqualTo(traceId);
    }

    /**
     * The gateway always sends one, but this service is reachable on its own port and must not fail —
     * nor lie — when it is called directly. It mints a trace of its own, so the response still carries
     * something an operator can search the log for.
     */
    @Test
    @DisplayName("a request with no trace still gets one of this service's own")
    void mintsATraceWhenCalledDirectly() {
        restTestClient
                .get()
                .uri(UNKNOWN_PRODUCT)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.traceId")
                .value(String.class, traceId -> assertThat(traceId).matches("[0-9a-f]{32}"));
    }

    private static String aTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
