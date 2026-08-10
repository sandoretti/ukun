package dev.sandoretti.ukun.api.customers.application;

import dev.sandoretti.ukun.api.identity.AccountRef;
import dev.sandoretti.ukun.api.identity.AccountsPort;
import dev.sandoretti.ukun.api.identity.CreateAccountCommand;
import dev.sandoretti.ukun.api.identity.EmailAlreadyExistsException;
import dev.sandoretti.ukun.api.identity.Role;
import dev.sandoretti.ukun.api.shared.error.FieldValidationException;
import dev.sandoretti.ukun.api.shared.observability.BusinessEvents;
import dev.sandoretti.ukun.api.stores.StoresApi;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;

/**
 * The signup saga of {@code docs/08-identidad-y-seguridad.md} §3, orchestrated here because signing up
 * crosses two databases and this is the service that owns the profile half.
 *
 * <h2>Why this is correct without a distributed transaction</h2>
 *
 * <p>Three steps, and the order is the argument:
 *
 * <ol>
 *   <li><strong>Reserve the account first.</strong> Email uniqueness is the one rule this service
 *       cannot check locally, so it is checked before anything is written here. Failing at this point
 *       leaves nothing behind — no orphan customer, which is the failure the opposite order produces.
 *   <li><strong>One local transaction</strong> for the profile, its address and its card. If it fails,
 *       the reservation is released best-effort; if that release also fails, ukun-auth's daily
 *       reconciliation collects the account. The correctness does not depend on the compensation.
 *   <li><strong>Activate.</strong> This is the real commit point: until it succeeds the account allows
 *       no login. It is retried, and if it still does not land the answer is {@code 202} rather than an
 *       error, because the customer <em>does</em> exist and repeating the signup finishes the job.
 * </ol>
 *
 * <p>The account is created disabled throughout, which is what makes an interrupted saga harmless: the
 * worst residue is a row that permits nothing and that a job deletes.
 *
 * <p><strong>This class is deliberately not {@code @Transactional}.</strong> It makes two calls over
 * the network, and a transaction spanning them would hold a database connection open across a remote
 * round trip — one slow dependency would drain the pool. The only transaction is inside
 * {@link CustomerRegistrar}, which is why that is a separate bean.
 *
 * <p>It replaces the legacy {@code ClienteValidator}, which asked {@code existsByCuenta_Correo} and
 * then saved, with a real race between the two calls. Uniqueness is now decided by the database that
 * owns the constraint, and losing the race is a {@code 409} instead of a duplicate row.
 */
@Service
public class SignupService {

    private static final Logger log = LoggerFactory.getLogger(SignupService.class);

    /** A signup creates a customer. Employees are created by the back-office (RF-11, issue #45). */
    private static final Set<Role> CUSTOMER_ONLY = Set.of(Role.CUSTOMER);

    /**
     * Three attempts at the activation — the initial one plus two retries — with a short growing
     * delay, as {@code docs/08} §3 specifies.
     *
     * <p>The budget is small on purpose. This runs inside a request, so the person is waiting; the
     * retries are there to ride out a restart or a blip, and anything longer is the {@code 202}'s job.
     */
    private static final RetryTemplate ACTIVATION_RETRIES = new RetryTemplate(RetryPolicy.builder()
            .maxRetries(2)
            .delay(Duration.ofMillis(200))
            .multiplier(2)
            .build());

    private final AccountsPort accounts;
    private final CustomerRegistrar registrar;
    private final StoresApi stores;

    SignupService(AccountsPort accounts, CustomerRegistrar registrar, StoresApi stores) {
        this.accounts = accounts;
        this.registrar = registrar;
        this.stores = stores;
    }

    /**
     * @return {@link SignupOutcome#COMPLETED} for the {@code 201}, {@link SignupOutcome#ACTIVATION_PENDING}
     *     for the {@code 202}
     * @throws EmailAlreadyExistsException propagated from ukun-auth, which the frontend paints under
     *     the email field
     */
    public SignupOutcome signUp(SignupRequest request) {
        validateLocally(request);

        // Step 1: the idempotency key of the whole saga. Issued here, sent with the reservation, and
        // repeated verbatim by any retry of this same signup — which is what makes the retry return
        // the same account instead of a second one.
        UUID registrationId = UUID.randomUUID();
        BusinessEvents.emit(
                Level.INFO, "signup.started", Map.of("registrationId", registrationId, "email", request.email()));

        AccountRef account = reserve(registrationId, request);

        long customerId = registerOrRelease(registrationId, account, request);

        return activate(registrationId, account, customerId);
    }

    /**
     * Step 0: the rules this service can decide on its own. Email uniqueness is deliberately not among
     * them — it does not know, and asking ukun-auth first would reintroduce the legacy's
     * check-then-write race across a network as well.
     */
    private void validateLocally(SignupRequest request) {
        Long favouriteStore = request.favoriteStoreId();
        if (favouriteStore != null && !stores.exists(favouriteStore)) {
            throw new FieldValidationException("favoriteStoreId", "does not match an existing store");
        }
    }

    /** Step 2: reserve the email with an account created disabled. */
    private AccountRef reserve(UUID registrationId, SignupRequest request) {
        try {
            AccountRef account = accounts.reserve(
                    registrationId,
                    new CreateAccountCommand(request.email(), request.password(), CUSTOMER_ONLY, false));

            BusinessEvents.emit(
                    Level.INFO,
                    "signup.account.reserved",
                    Map.of("registrationId", registrationId, "accountId", account.accountId()));

            return account;

        } catch (EmailAlreadyExistsException taken) {
            // The email is the subject of this event, which is why it may carry one at all (docs/11 §4).
            BusinessEvents.emit(Level.INFO, "signup.email.duplicate", Map.of("email", request.email()));
            throw taken;
        }
    }

    /** Step 3: the single local transaction, with its compensation. */
    private long registerOrRelease(UUID registrationId, AccountRef account, SignupRequest request) {
        try {
            return registrar.register(account.accountId(), request);

        } catch (RuntimeException localFailure) {
            compensate(registrationId, account);
            throw localFailure;
        }
    }

    /**
     * Best-effort by design. A failure here is logged at ERROR and swallowed, because the caller's
     * problem is the local failure that got us here — replacing it with the compensation's exception
     * would hide the actual cause, and the account is collected by reconciliation either way.
     */
    private void compensate(UUID registrationId, AccountRef account) {
        try {
            accounts.release(account.accountId());
        } catch (RuntimeException releaseFailure) {
            log.error("Releasing the reserved account failed, leaving it to reconciliation", releaseFailure);
        }
        BusinessEvents.emit(
                Level.ERROR,
                "signup.compensation",
                Map.of("registrationId", registrationId, "accountId", account.accountId(), "step", "register"));
    }

    /** Step 4: the commit point, retried, and the {@code 202} if it still does not land. */
    private SignupOutcome activate(UUID registrationId, AccountRef account, long customerId) {
        try {
            ACTIVATION_RETRIES.execute(() -> {
                accounts.activate(account.accountId());
                return null;
            });

        } catch (RetryException exhausted) {
            BusinessEvents.emit(
                    Level.ERROR,
                    "signup.activation.pending",
                    Map.of("registrationId", registrationId, "accountId", account.accountId(), "attempts", 3));

            return SignupOutcome.ACTIVATION_PENDING;
        }

        BusinessEvents.emit(
                Level.INFO,
                "signup.completed",
                Map.of(
                        "registrationId", registrationId,
                        "accountId", account.accountId(),
                        "customerId", customerId));

        return SignupOutcome.COMPLETED;
    }
}
