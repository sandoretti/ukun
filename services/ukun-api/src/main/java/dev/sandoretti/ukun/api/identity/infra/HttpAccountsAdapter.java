package dev.sandoretti.ukun.api.identity.infra;

import dev.sandoretti.ukun.api.identity.AccountRef;
import dev.sandoretti.ukun.api.identity.AccountsPort;
import dev.sandoretti.ukun.api.identity.CreateAccountCommand;
import dev.sandoretti.ukun.api.identity.EmailAlreadyExistsException;
import dev.sandoretti.ukun.api.identity.Role;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * {@link AccountsPort} over HTTP, against the three operations of
 * {@code docs/api/openapi/ukun-auth.yaml}. The only place in this service that knows ukun-auth is
 * reached over the network at all.
 *
 * <p>It translates one status into a domain exception and leaves the rest alone. A {@code 409} on a
 * reservation is a business outcome the frontend paints under the email field, so it becomes
 * {@link EmailAlreadyExistsException}; a {@code 500} or a connection refused is not, and is allowed to
 * surface as the client's own exception so the saga can decide whether to retry or compensate. Turning
 * every failure into one flat exception would take that decision away from the only code able to make
 * it.
 *
 * <p>Nothing here logs the command, the token or the response. {@code CreateAccountCommand} masks its
 * password in {@code toString()} for the times something else does
 * ({@code docs/11-observabilidad-y-logging.md} §4).
 */
@Component
class HttpAccountsAdapter implements AccountsPort {

    /** The registration of {@code application.yaml}; the interceptor asks for a token by this name. */
    static final String REGISTRATION_ID = "ukun-auth";

    private static final String ACCOUNTS = "/internal/accounts";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final RestClient restClient;
    private final ClientRegistrationRepository registrations;

    HttpAccountsAdapter(
            @Qualifier("accountsRestClient") RestClient restClient, ClientRegistrationRepository registrations) {
        this.restClient = restClient;
        this.registrations = registrations;
    }

    /**
     * The response body of {@code POST /internal/accounts}, its {@code AccountRef} schema. Declared
     * here rather than reused from the port's own {@code AccountRef}: that one carries {@code created},
     * which is not a field of the contract but a reading of the status code.
     */
    private record AccountRefResponse(UUID accountId) {}

    private record CreateAccountBody(String email, String password, Set<Role> roles, boolean enabled) {

        /** The password is a component of this record too, so it masks it for the same reason. */
        @Override
        public String toString() {
            return "CreateAccountBody[email=%s, password=***, roles=%s, enabled=%s]".formatted(email, roles, enabled);
        }
    }

    @Override
    public AccountRef reserve(UUID registrationId, CreateAccountCommand command) {
        requireConfiguredSecret();

        var response = restClient
                .post()
                .uri(ACCOUNTS)
                .header(IDEMPOTENCY_KEY, registrationId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateAccountBody(command.email(), command.password(), command.roles(), command.enabled()))
                .retrieve()
                .onStatus(
                        status -> status.value() == HttpStatus.CONFLICT.value(),
                        (request, conflict) -> {
                            throw new EmailAlreadyExistsException();
                        })
                .toEntity(AccountRefResponse.class);

        boolean created = response.getStatusCode().value() == HttpStatus.CREATED.value();

        return new AccountRef(response.getBody().accountId(), created);
    }

    @Override
    public void activate(UUID accountId) {
        requireConfiguredSecret();

        restClient.post().uri(ACCOUNTS + "/{accountId}:activate", accountId).retrieve().toBodilessEntity();
    }

    @Override
    public void release(UUID accountId) {
        requireConfiguredSecret();

        restClient.post().uri(ACCOUNTS + "/{accountId}:release", accountId).retrieve().toBodilessEntity();
    }

    /**
     * The client secret defaults to empty so that a service nobody has wired the saga into still
     * starts, and so the integration suite does not need it ({@code application.yaml} states the
     * trade-off). The price of that default is this check: without it the first real call would fail
     * inside the token endpoint as an opaque {@code 401 invalid_client}, several layers away from the
     * missing environment variable that actually caused it.
     */
    private void requireConfiguredSecret() {
        var registration = registrations.findByRegistrationId(REGISTRATION_ID);
        if (registration == null || !StringUtils.hasText(registration.getClientSecret())) {
            throw new IllegalStateException(
                    "UKUN_OAUTH2_API_SECRET is not set: this service cannot obtain a token for ukun-auth");
        }
    }
}
