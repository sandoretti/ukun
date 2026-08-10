package dev.sandoretti.ukun.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The server half of the signup saga: the three operations of
 * {@code docs/api/openapi/ukun-auth.yaml}, against a real Authorization Server and a real database.
 *
 * <p>The condition issue #25 states — <em>«reintentar la misma alta no crea dos cuentas ni deja un
 * account huérfano»</em> — is what {@link #replayingTheSameKeyReturnsTheSameAccount()} and
 * {@link #releaseFreesTheEmailAndTheKey()} assert here; the other half of it, a saga interrupted
 * mid-flight, belongs to ukun-api's {@code SignupIT} and to {@link AccountReconciliationIT}.
 *
 * <p>Tokens are obtained the way ukun-api obtains them, through {@code client_credentials} against
 * the running server. Nothing is stubbed: a test that mocked the decoder would assert that a mock
 * returns what it was told to, and the thing most worth checking here is precisely that a token
 * without {@code accounts:write} does not open these endpoints.
 *
 * <p>Every test mints its own email. The suite shares one database and nothing truncates between
 * classes, so a fixed address would make these tests depend on the order they happen to run in — the
 * failure mode object mothers exist to avoid ({@code docs/13-testing.md} §1).
 */
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.liquibase.contexts=prod",
            "ukun.oauth2.gateway.secret=" + AbstractIntegrationTest.GATEWAY_SECRET,
            "ukun.oauth2.api.secret=" + AbstractIntegrationTest.API_SECRET,
            // No outbound HTTPS from an *IT (docs/13 §1). The length rule of the policy still applies,
            // and PasswordPolicyIT covers the breach check with a checker of its own.
            "ukun.password.compromised-check.enabled=false"
        })
class InternalAccountsIT extends AbstractIntegrationTest {

    private static final String ACCOUNTS = "/internal/accounts";

    /** Twelve characters, the minimum of docs/08 §8 — not the subject of this class. */
    private static final String PASSWORD = "correct-horse-battery";

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {};

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, ACCOUNTS_DATABASE);
    }

    @Autowired
    private RestTestClient http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JWKSource<SecurityContext> jwkSource;

    @Test
    @DisplayName("a reservation creates the account disabled, with its roles, and answers 201")
    void reservesTheEmailWithADisabledAccount() {
        String email = anEmail();

        Map<String, Object> body = reserve(UUID.randomUUID(), email, PASSWORD, "CUSTOMER")
                .expectStatus()
                .isCreated()
                .expectBody(JSON)
                .returnResult()
                .getResponseBody();

        UUID accountId = UUID.fromString((String) body.get("accountId"));

        // Disabled is the whole point of step 2: an abandoned reservation must allow no login.
        assertThat(enabledOf(accountId)).isFalse();
        assertThat(rolesOf(accountId)).containsExactly("CUSTOMER");
    }

    /**
     * The property the issue is named after. Two calls, one key, one account — and the second answers
     * {@code 200} rather than {@code 201}, which is how ukun-api can tell a retry from a first
     * attempt without asking again.
     */
    @Test
    @DisplayName("replaying the same Idempotency-Key returns the same account and creates nothing")
    void replayingTheSameKeyReturnsTheSameAccount() {
        String email = anEmail();
        UUID key = UUID.randomUUID();

        UUID first = accountIdOf(reserve(key, email, PASSWORD, "CUSTOMER").expectStatus().isCreated());
        UUID replayed = accountIdOf(reserve(key, email, PASSWORD, "CUSTOMER").expectStatus().isOk());

        assertThat(replayed).isEqualTo(first);
        assertThat(accountsWithEmail(email)).isEqualTo(1);
    }

    /**
     * A different key against a taken email is not a retry, it is a second person. An account reserved
     * by one attempt is never handed to another ({@code docs/08} §3), and the code is the one the
     * frontend paints under the email field.
     */
    @Test
    @DisplayName("a different key against a taken email is a 409 EMAIL_ALREADY_EXISTS")
    void refusesToHandAReservedEmailToAnotherAttempt() {
        String email = anEmail();
        reserve(UUID.randomUUID(), email, PASSWORD, "CUSTOMER").expectStatus().isCreated();

        Map<String, Object> problem = reserve(UUID.randomUUID(), email, PASSWORD, "CUSTOMER")
                .expectStatus()
                .isEqualTo(HttpStatus.CONFLICT)
                .expectBody(JSON)
                .returnResult()
                .getResponseBody();

        assertThat(problem).containsEntry("code", "EMAIL_ALREADY_EXISTS").containsKey("traceId");
        assertThat(accountsWithEmail(email)).isEqualTo(1);
    }

    /**
     * The half of "alta de empleado" that belongs to v0.4. The endpoint that creates an employee
     * profile is {@code POST /api/admin/employees} in ukun-api and is v0.7 (issue #45); what this
     * release owes it is that the same reservation works with the roles of ADR-0006, where ADMIN only
     * ever travels alongside EMPLOYEE.
     */
    @Test
    @DisplayName("the same reservation serves an employee who is also an admin")
    void reservesAnAccountForAnAdminEmployee() {
        UUID accountId = accountIdOf(reserve(UUID.randomUUID(), anEmail(), PASSWORD, "EMPLOYEE", "ADMIN")
                .expectStatus()
                .isCreated());

        assertThat(rolesOf(accountId)).containsExactlyInAnyOrder("EMPLOYEE", "ADMIN");
    }

    /**
     * Activation is the real commit point, and it is idempotent because ukun-api retries it with
     * backoff: answering an error on the second attempt would turn a delivered activation into a
     * failed signup.
     */
    @Test
    @DisplayName("activation enables the account and repeating it is still a 204")
    void activationIsIdempotent() {
        UUID accountId = accountIdOf(
                reserve(UUID.randomUUID(), anEmail(), PASSWORD, "CUSTOMER").expectStatus().isCreated());

        activate(accountId).expectStatus().isNoContent();
        assertThat(enabledOf(accountId)).isTrue();

        activate(accountId).expectStatus().isNoContent();
        assertThat(enabledOf(accountId)).isTrue();
    }

    /** Committing something that was never reserved is a real failure, unlike a repeated activation. */
    @Test
    @DisplayName("activating an account that does not exist is a 404 RESOURCE_NOT_FOUND")
    void activatingAnUnknownAccountIsNotFound() {
        Map<String, Object> problem = activate(UUID.randomUUID())
                .expectStatus()
                .isNotFound()
                .expectBody(JSON)
                .returnResult()
                .getResponseBody();

        assertThat(problem).containsEntry("code", "RESOURCE_NOT_FOUND").containsEntry("resource", "account");
    }

    /**
     * The compensation, and the cascade behind it. The idempotency key has to go with the account:
     * left behind, a retry of that same signup would resolve to an account that no longer exists
     * (baseline, changeset {@code 001-10}).
     */
    @Test
    @DisplayName("release deletes the account and its idempotency key, freeing the email")
    void releaseFreesTheEmailAndTheKey() {
        String email = anEmail();
        UUID key = UUID.randomUUID();
        UUID accountId = accountIdOf(reserve(key, email, PASSWORD, "CUSTOMER").expectStatus().isCreated());

        release(accountId).expectStatus().isNoContent();

        assertThat(accountsWithEmail(email)).isZero();
        assertThat(registrationsUnder(key)).isZero();

        // And the email is genuinely free: a fresh signup can take it.
        reserve(UUID.randomUUID(), email, PASSWORD, "CUSTOMER").expectStatus().isCreated();
    }

    /**
     * Best-effort means the caller wants an outcome, not a report. The email is free either way, so
     * there is nothing to retry and nothing to tell ukun-api about.
     */
    @Test
    @DisplayName("releasing an account that is already gone is still a 204")
    void releasingAnUnknownAccountSucceeds() {
        release(UUID.randomUUID()).expectStatus().isNoContent();
    }

    @Test
    @DisplayName("without a token the endpoints answer 401 UNAUTHENTICATED as a ProblemDetail")
    void rejectsAnAnonymousCaller() {
        Map<String, Object> problem = http.post()
                .uri(ACCOUNTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody(anEmail(), PASSWORD, "CUSTOMER"))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody(JSON)
                .returnResult()
                .getResponseBody();

        // The shape matters as much as the status: an empty body is what Spring Security would give
        // by default, and ukun-api's adapter would have nothing to read.
        assertThat(problem).containsEntry("code", "UNAUTHENTICATED").containsKey("traceId");
    }

    /**
     * Rule 5 of the {@code spring-auth} skill, asserted rather than trusted. The token is genuine —
     * signed with this server's own key — and is refused purely for lacking the scope, which is the
     * only thing that separates ukun-api from any other holder of a valid token.
     */
    @Test
    @DisplayName("a valid token without accounts:write is a 403 FORBIDDEN")
    void rejectsATokenWithoutTheScope() {
        String weakToken = AccessTokens.signedWithScopes(jwkSource, "http://localhost:9000", "openid", "profile");

        Map<String, Object> problem = http.post()
                .uri(ACCOUNTS)
                .headers(headers -> headers.setBearerAuth(weakToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody(anEmail(), PASSWORD, "CUSTOMER"))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(JSON)
                .returnResult()
                .getResponseBody();

        assertThat(problem).containsEntry("code", "FORBIDDEN");
    }

    /** The header is required by the contract, so its absence is a rejected request, not a default. */
    @Test
    @DisplayName("a reservation without an Idempotency-Key is a 400")
    void requiresTheIdempotencyKey() {
        http.post()
                .uri(ACCOUNTS)
                .headers(headers -> headers.setBearerAuth(AccessTokens.of(http, API_SECRET)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody(anEmail(), PASSWORD, "CUSTOMER"))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    // ---------------------------------------------------------------------------------------------

    private RestTestClient.ResponseSpec reserve(UUID key, String email, String password, String... roles) {
        return http.post()
                .uri(ACCOUNTS)
                .headers(headers -> headers.setBearerAuth(AccessTokens.of(http, API_SECRET)))
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody(email, password, roles))
                .exchange();
    }

    private RestTestClient.ResponseSpec activate(UUID accountId) {
        return post(ACCOUNTS + "/" + accountId + ":activate");
    }

    private RestTestClient.ResponseSpec release(UUID accountId) {
        return post(ACCOUNTS + "/" + accountId + ":release");
    }

    private RestTestClient.ResponseSpec post(String uri) {
        return http.post()
                .uri(uri)
                .headers(headers -> headers.setBearerAuth(AccessTokens.of(http, API_SECRET)))
                .exchange();
    }

    private static Map<String, Object> requestBody(String email, String password, String... roles) {
        return Map.of("email", email, "password", password, "roles", List.of(roles), "enabled", false);
    }

    private static UUID accountIdOf(RestTestClient.ResponseSpec response) {
        return UUID.fromString(
                (String) response.expectBody(JSON).returnResult().getResponseBody().get("accountId"));
    }

    /** A fresh address per test, so no two tests can collide on {@code ux_account_email}. */
    private static String anEmail() {
        return "signup-" + UUID.randomUUID() + "@ukun.dev";
    }

    private boolean enabledOf(UUID accountId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT enabled FROM account WHERE id = ?", Boolean.class, accountId));
    }

    private List<String> rolesOf(UUID accountId) {
        return jdbc.queryForList("SELECT role FROM account_role WHERE account_id = ?", String.class, accountId);
    }

    private int accountsWithEmail(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM account WHERE email = ?", Integer.class, email);
    }

    private int registrationsUnder(UUID key) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM registration_request WHERE idempotency_key = ?", Integer.class, key);
    }
}
