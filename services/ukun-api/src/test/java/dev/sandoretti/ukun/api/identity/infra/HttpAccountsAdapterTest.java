package dev.sandoretti.ukun.api.identity.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import dev.sandoretti.ukun.api.identity.AccountRef;
import dev.sandoretti.ukun.api.identity.CreateAccountCommand;
import dev.sandoretti.ukun.api.identity.EmailAlreadyExistsException;
import dev.sandoretti.ukun.api.identity.Role;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The translation between {@code AccountsPort} and the three operations of
 * {@code docs/api/openapi/ukun-auth.yaml}, against a stubbed HTTP boundary.
 *
 * <p>A unit test and not an IT because the other service does not exist yet to talk to: its
 * {@code /internal/accounts} controllers arrive with the signup saga. What can be pinned down now is
 * that this adapter sends what the frozen contract says and reads back what it promises — which is
 * exactly the part the saga will build on.
 */
class HttpAccountsAdapterTest {

    private static final UUID REGISTRATION_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID ACCOUNT_ID = UUID.fromString("8f2c1e40-0000-4000-8000-000000000001");

    private static final CreateAccountCommand SIGNUP =
            new CreateAccountCommand("customer@ukun.dev", "a-very-long-password", Set.of(Role.CUSTOMER), false);

    private MockRestServiceServer server;
    private HttpAccountsAdapter adapter;

    @BeforeEach
    void bindTheBoundary() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ukun-auth:9000");
        server = MockRestServiceServer.bindTo(builder).build();

        adapter = new HttpAccountsAdapter(builder.build(), registrations("a-secret"));
    }

    @Test
    @DisplayName("a reservation sends the idempotency key and the credentials the contract asks for")
    void shouldReserveAnAccount() {
        server.expect(requestTo("http://ukun-auth:9000/internal/accounts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", REGISTRATION_ID.toString()))
                .andExpect(jsonPath("$.email").value("customer@ukun.dev"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"accountId\":\"%s\"}".formatted(ACCOUNT_ID)));

        AccountRef reserved = adapter.reserve(REGISTRATION_ID, SIGNUP);

        assertThat(reserved).isEqualTo(new AccountRef(ACCOUNT_ID, true));
        server.verify();
    }

    /**
     * The retry of the same signup. It is the same account and nothing was created, and the saga has to
     * be able to tell — that is the whole of what {@code created} is for.
     */
    @Test
    @DisplayName("replaying the same key returns the same account as an existing one")
    void shouldReportAReplayAsNotCreated() {
        server.expect(requestTo("http://ukun-auth:9000/internal/accounts"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"accountId\":\"%s\"}".formatted(ACCOUNT_ID)));

        assertThat(adapter.reserve(REGISTRATION_ID, SIGNUP)).isEqualTo(new AccountRef(ACCOUNT_ID, false));
    }

    @Test
    @DisplayName("a taken email becomes the code the frontend paints under the field")
    void shouldTranslateAConflictIntoTheDomainException() {
        server.expect(requestTo("http://ukun-auth:9000/internal/accounts"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"code\":\"EMAIL_ALREADY_EXISTS\"}"));

        assertThatThrownBy(() -> adapter.reserve(REGISTRATION_ID, SIGNUP))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .satisfies(thrown -> assertThat(((EmailAlreadyExistsException) thrown).getCode().name())
                        .isEqualTo("EMAIL_ALREADY_EXISTS"));
    }

    /**
     * Anything that is not a business outcome stays a client exception. The saga is the only code able
     * to decide whether a failed activation deserves a retry or a compensation, and flattening every
     * failure into one exception here would take that decision away from it.
     */
    @Test
    @DisplayName("a failure that is not a business outcome is left for the caller to decide on")
    void shouldNotSwallowATransportFailure() {
        server.expect(requestTo("http://ukun-auth:9000/internal/accounts/%s:activate".formatted(ACCOUNT_ID)))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> adapter.activate(ACCOUNT_ID)).isNotInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    @DisplayName("the compensation posts to :release")
    void shouldReleaseAReservation() {
        server.expect(requestTo("http://ukun-auth:9000/internal/accounts/%s:release".formatted(ACCOUNT_ID)))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        adapter.release(ACCOUNT_ID);

        server.verify();
    }

    /**
     * The other half of the empty-secret default of {@code application.yaml}: the call is refused here,
     * naming the environment variable, instead of failing three layers away as an opaque
     * {@code invalid_client} from the token endpoint.
     */
    @Test
    @DisplayName("a blank client secret is refused by name, not as a 401 from somewhere else")
    void shouldRefuseToCallWithoutASecret() {
        var unconfigured = new HttpAccountsAdapter(RestClient.builder().build(), registrations(""));

        assertThatThrownBy(() -> unconfigured.reserve(REGISTRATION_ID, SIGNUP))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UKUN_OAUTH2_API_SECRET");
    }

    @Test
    @DisplayName("the command keeps the password out of its own toString")
    void shouldNotPrintThePassword() {
        assertThat(SIGNUP.toString()).doesNotContain("a-very-long-password").contains("customer@ukun.dev");
    }

    private static ClientRegistrationRepository registrations(String secret) {
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId(
                        HttpAccountsAdapter.REGISTRATION_ID)
                .clientId("ukun-api")
                .clientSecret(secret)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("http://ukun-auth:9000/oauth2/token")
                .scope("accounts:write")
                .build());
    }
}
