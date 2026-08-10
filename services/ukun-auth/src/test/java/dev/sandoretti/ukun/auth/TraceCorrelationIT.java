package dev.sandoretti.ukun.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * This service's end of issue #30, the mirror of the class of the same name in {@code ukun-api}: the
 * {@code traceId} it reports is the one it was called with.
 *
 * <p>It matters most here, and the reason is the signup saga. {@code ukun-api} reserves an account
 * against {@code /internal/accounts} and completes it afterwards; when that goes wrong it logs
 * {@code signup.compensation} on one side and this service logs whatever it refused on the other.
 * Before this release those were two lines with no way to tell whether they belonged to the same
 * attempt.
 *
 * <p>The 401 is the cheapest error in the service that produces a {@code ProblemDetail}, and it needs
 * no account, no token and no fixture — the subject here is the identifier, not the rejection, which
 * {@code InternalAccountsIT} already covers.
 */
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.liquibase.contexts=prod",
            "ukun.oauth2.gateway.secret=" + AbstractIntegrationTest.GATEWAY_SECRET,
            "ukun.oauth2.api.secret=" + AbstractIntegrationTest.API_SECRET,
            "ukun.password.compromised-check.enabled=false"
        })
class TraceCorrelationIT extends AbstractIntegrationTest {

    private static final String TRACEPARENT = "traceparent";

    private static final String ACCOUNTS = "/internal/accounts";

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {};

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, ACCOUNTS_DATABASE);
    }

    @Autowired
    private RestTestClient http;

    @Test
    @DisplayName("an error reports the trace id ukun-api called with")
    void reportsTheIncomingTraceId() {
        String traceId = aTraceId();

        Map<String, Object> problem = anonymousReservation(
                        "00-%s-%s-01".formatted(traceId, aTraceId().substring(0, 16)))
                .expectStatus()
                .isUnauthorized()
                .expectBody(JSON)
                .returnResult()
                .getResponseBody();

        assertThat(problem).containsEntry("traceId", traceId);
    }

    /**
     * Nobody should reach this service without a trace — {@code ukun-api} always carries one and the
     * gateway does not route {@code /internal/**} at all ({@code docs/08} §3). It still answers with a
     * usable identifier rather than an empty field, because an error contract that degrades into
     * {@code null} is the failure {@code docs/12-error-contract.md} exists to prevent.
     */
    @Test
    @DisplayName("a call with no trace still gets one of this service's own")
    void mintsATraceWhenCalledWithoutOne() {
        Map<String, Object> problem = anonymousReservation(null)
                .expectStatus()
                .isUnauthorized()
                .expectBody(JSON)
                .returnResult()
                .getResponseBody();

        assertThat(problem).extracting("traceId").asString().matches("[0-9a-f]{32}");
    }

    /** A well-formed reservation with no token: rejected before anything is read or written. */
    private RestTestClient.ResponseSpec anonymousReservation(String traceparent) {
        RestTestClient.RequestBodySpec request = http.post()
                .uri(ACCOUNTS)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", UUID.randomUUID().toString());

        if (traceparent != null) {
            request = request.header(TRACEPARENT, traceparent);
        }

        return request.body(
                        """
                        {"email":"trace@example.com","password":"correct-horse-battery","role":"CUSTOMER"}""")
                .exchange();
    }

    private static String aTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
