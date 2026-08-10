package dev.sandoretti.ukun.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.authentication.password.CompromisedPasswordDecision;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The password policy of {@code docs/08-identidad-y-seguridad.md} §8, exercised through the endpoint
 * that applies it.
 *
 * <p>The three assertions are chosen to pin the two rules that are <strong>absences</strong>, because
 * absences are what a later change quietly reintroduces: there is a real minimum, there is
 * <em>no</em> maximum, and a long password with no composition rule broken is still refused if it is
 * in a breach corpus. The legacy is the counter-example on all three counts — plaintext, capped at
 * sixteen characters, no corpus check.
 *
 * <p>{@link CompromisedPasswordChecker} is replaced with {@code @MockitoBean} — {@code @MockBean} was
 * removed in Boot 4 ({@code .claude/skills/README.md}). Replacing it is within the rule of
 * {@code docs/13-testing.md} §"Mocks solo en fronteras": the real implementation is an HTTPS call to
 * api.pwnedpasswords.com, which is exactly the kind of external boundary that rule allows and an
 * {@code *IT} must not depend on.
 */
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.liquibase.contexts=prod",
            "ukun.oauth2.gateway.secret=" + AbstractIntegrationTest.GATEWAY_SECRET,
            "ukun.oauth2.api.secret=" + AbstractIntegrationTest.API_SECRET
        })
class PasswordPolicyIT extends AbstractIntegrationTest {

    private static final String ACCOUNTS = "/internal/accounts";

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {};

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, ACCOUNTS_DATABASE);
    }

    @Autowired
    private RestTestClient http;

    @MockitoBean
    private CompromisedPasswordChecker compromisedPasswords;

    /**
     * Nothing is breached unless a test says so. The corpus is not what most of these assertions are
     * about, and stating it once keeps each test showing only the rule it exercises.
     */
    @BeforeEach
    void noPasswordIsBreachedByDefault() {
        given(compromisedPasswords.check(anyString())).willReturn(new CompromisedPasswordDecision(false));
    }

    @Test
    @DisplayName("eleven characters are refused, and the error names the password field")
    void refusesAPasswordShorterThanTheMinimum() {
        Map<String, Object> problem = reserve("elevenchars")
                .expectStatus()
                .isBadRequest()
                .expectBody(JSON)
                .returnResult()
                .getResponseBody();

        // VALIDATION_FAILED and not an invented WEAK_PASSWORD: the registry of docs/12 is closed, and
        // the field is what lets the frontend paint the message under the right input.
        assertThat(problem).containsEntry("code", "VALIDATION_FAILED");
        assertThat(errorFieldsOf(problem)).containsExactly("password");
    }

    /**
     * The legacy capped passwords at {@code VARCHAR(16)} while storing them in the clear. Sixty-four
     * characters is the length NIST SP 800-63B asks to be supported, and it is four times what the
     * legacy allowed — the artificial maximum is gone.
     */
    @Test
    @DisplayName("a 64-character passphrase is accepted: no artificial maximum")
    void acceptsALongPassphrase() {
        reserve("a".repeat(64)).expectStatus().isCreated();
    }

    /**
     * The limit that is <em>not</em> policy: BCrypt ignores everything past its 72nd byte and Spring
     * Security refuses to encode a longer password rather than truncating it silently.
     *
     * <p>This test exists because the first version of this class asserted 80 characters were
     * accepted and got a {@code 500} with a stack trace. The behaviour under test is therefore the
     * fix: the limit surfaces as a rejected field, the way every other rule of the policy does, so
     * ukun-api can pass it to the form instead of showing "something went wrong".
     */
    @Test
    @DisplayName("past BCrypt's 72-byte limit the answer is a 400 on the field, never a 500")
    void refusesAPasswordLongerThanTheAlgorithmAccepts() {
        Map<String, Object> problem = reserve("a".repeat(73))
                .expectStatus()
                .isBadRequest()
                .expectBody(JSON)
                .returnResult()
                .getResponseBody();

        assertThat(problem).containsEntry("code", "VALIDATION_FAILED");
        assertThat(errorFieldsOf(problem)).containsExactly("password");
    }

    /**
     * The check that replaces composition rules. This password is long, mixed-case-free, symbol-free
     * and would sail past any "one uppercase, one digit" policy — and it is in every wordlist, which
     * is the only fact about it that matters.
     */
    @Test
    @DisplayName("a long but breached password is refused")
    void refusesAKnownBreachedPassword() {
        given(compromisedPasswords.check("correcthorsebatterystaple"))
                .willReturn(new CompromisedPasswordDecision(true));

        Map<String, Object> problem = reserve("correcthorsebatterystaple")
                .expectStatus()
                .isBadRequest()
                .expectBody(JSON)
                .returnResult()
                .getResponseBody();

        assertThat(problem).containsEntry("code", "VALIDATION_FAILED");
        assertThat(errorFieldsOf(problem)).containsExactly("password");
    }

    private RestTestClient.ResponseSpec reserve(String password) {
        return http.post()
                .uri(ACCOUNTS)
                .headers(headers -> headers.setBearerAuth(AccessTokens.of(http, API_SECRET)))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "email",
                        "policy-" + UUID.randomUUID() + "@ukun.dev",
                        "password",
                        password,
                        "roles",
                        List.of("CUSTOMER"),
                        "enabled",
                        false))
                .exchange();
    }

    @SuppressWarnings("unchecked")
    private static List<String> errorFieldsOf(Map<String, Object> problem) {
        return ((List<Map<String, Object>>) problem.get("errors"))
                .stream().map(error -> (String) error.get("field")).toList();
    }
}
