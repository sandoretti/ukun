package dev.sandoretti.ukun.api.identity.infra;

import dev.sandoretti.ukun.api.identity.AccountRef;
import dev.sandoretti.ukun.api.identity.AccountRejectedException;
import dev.sandoretti.ukun.api.identity.AccountsPort;
import dev.sandoretti.ukun.api.identity.CreateAccountCommand;
import dev.sandoretti.ukun.api.identity.EmailAlreadyExistsException;
import dev.sandoretti.ukun.api.shared.error.ValidationError;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

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
 * <p>The command is serialised as the request body directly. It has exactly the four fields of the
 * {@code CreateAccountRequest} schema, so a body type of its own would be the same record written
 * twice — including the hand-written {@code toString()} that masks the password, which is precisely
 * the kind of rule that gets updated in one copy and not the other. If the contract ever grows a
 * field this port does not have, that is the moment to split them, not before.
 *
 * <p>Nothing here logs the command, the token or the response. {@code CreateAccountCommand} masks its
 * password in {@code toString()} for the times something else does
 * ({@code docs/11-observabilidad-y-logging.md} §4).
 */
@Component
class HttpAccountsAdapter implements AccountsPort {

    private static final Logger log = LoggerFactory.getLogger(HttpAccountsAdapter.class);

    /** The registration of {@code application.yaml}; the interceptor asks for a token by this name. */
    static final String REGISTRATION_ID = "ukun-auth";

    private static final String ACCOUNTS = "/internal/accounts";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final RestClient restClient;
    private final ClientRegistrationRepository registrations;
    private final ObjectMapper objectMapper;

    HttpAccountsAdapter(
            @Qualifier("accountsRestClient") RestClient restClient,
            ClientRegistrationRepository registrations,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.registrations = registrations;
        this.objectMapper = objectMapper;
    }

    /**
     * Enough of ukun-auth's {@code ProblemDetail} to forward a rejected field. Everything else of that
     * body — {@code detail}, {@code traceId}, {@code type} — is deliberately dropped: those describe
     * the other service's handling of the request, and this service issues its own.
     */
    private record ValidationProblem(List<ValidationError> errors) {}

    /**
     * The response body of {@code POST /internal/accounts}, its {@code AccountRef} schema. Declared
     * here rather than reused from the port's own {@code AccountRef}: that one carries {@code created},
     * which is not a field of the contract but a reading of the status code.
     */
    private record AccountRefResponse(UUID accountId) {}

    @Override
    public AccountRef reserve(UUID registrationId, CreateAccountCommand command) {
        requireConfiguredSecret();

        var response = restClient
                .post()
                .uri(ACCOUNTS)
                .header(IDEMPOTENCY_KEY, registrationId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(command)
                .retrieve()
                .onStatus(
                        status -> status.value() == HttpStatus.CONFLICT.value(),
                        (request, conflict) -> {
                            throw new EmailAlreadyExistsException();
                        })
                // The password policy lives in ukun-auth and only ukun-auth can apply it — it is the
                // service that owns credentials and the one that can consult a breach corpus. Its
                // verdict therefore arrives as a 400, and without this it would surface as an
                // unhandled client error: "unexpected error" for typing a short password.
                .onStatus(
                        status -> status.value() == HttpStatus.BAD_REQUEST.value(),
                        (request, rejected) -> {
                            throw rejectionOf(rejected);
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
     * Turns ukun-auth's rejection into this service's own, keeping the fields it named.
     *
     * <p>A body that cannot be read, or one with no {@code errors} to forward, is <strong>not</strong>
     * translated: it is left as the client's own exception so it surfaces as a {@code 500} and gets
     * logged with a stack trace. Inventing a field name would put a message under an input the person
     * did not get wrong, which is worse than a generic failure.
     */
    private RuntimeException rejectionOf(ClientHttpResponse rejected) {
        try {
            ValidationProblem problem = objectMapper.readValue(rejected.getBody(), ValidationProblem.class);

            if (problem.errors() != null && !problem.errors().isEmpty()) {
                return new AccountRejectedException(problem.errors());
            }
        } catch (IOException | RuntimeException unreadable) {
            log.warn("ukun-auth rejected the reservation with a body this service could not read", unreadable);
        }
        return new RestClientException("ukun-auth rejected the reservation without naming a field");
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
