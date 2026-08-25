package dev.sandoretti.ukun.api.identity;

import dev.sandoretti.ukun.api.shared.observability.BusinessEvents;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;

/**
 * The registration saga of {@code docs/08-identidad-y-seguridad.md} §3, orchestrated here because
 * creating a person crosses two databases and this is the module that owns the way out to ukun-auth.
 *
 * <h2>Why this is correct without a distributed transaction</h2>
 *
 * <p>Three steps, and the order is the argument:
 *
 * <ol>
 *   <li><strong>Reserve the account first.</strong> Email uniqueness is the one rule this service
 *       cannot check locally, so it is checked before anything is written here. Failing at this point
 *       leaves nothing behind — no orphan profile, which is the failure the opposite order produces.
 *   <li><strong>One local transaction</strong> for the profile and whatever it owns. If it fails, the
 *       reservation is released best-effort; if that release also fails, ukun-auth's daily
 *       reconciliation collects the account. The correctness does not depend on the compensation.
 *   <li><strong>Activate.</strong> This is the real commit point: until it succeeds the account allows
 *       no login. It is retried, and if it still does not land the answer is {@code 202} rather than an
 *       error, because the person <em>does</em> exist and repeating the registration finishes the job.
 * </ol>
 *
 * <p>The account is created disabled throughout, which is what makes an interrupted saga harmless: the
 * worst residue is a row that permits nothing and that a job deletes.
 *
 * <p><strong>This class is deliberately not {@code @Transactional}.</strong> It makes two calls over
 * the network, and a transaction spanning them would hold a database connection open across a remote
 * round trip — one slow dependency would drain the pool. The only transaction is the caller's
 * {@link LocalProfileWriter}, which is why that has to be a bean of its own.
 *
 * <p>It replaces the legacy {@code ClienteValidator}/{@code EmpleadoValidator}, which asked
 * {@code existsByCuenta_Correo} and then saved, with a real race between the two calls. Uniqueness is
 * now decided by the database that owns the constraint, and losing the race is a {@code 409} instead
 * of a duplicate row.
 *
 * <h2>Why it lives in {@code identity} and not next to a profile</h2>
 *
 * <p>It was the signup's own service until issue #45, when the back-office needed the very same saga
 * to create an employee (RF-11, {@code docs/08} §3, which names this class). {@code staff} cannot
 * depend on {@code customers} — the declared graph of ADR-0017 §1 says so and {@code ModulithTest}
 * asserts it — so the choice was one orchestrator here or two copies of a compensation nobody exercises
 * by hand. What each caller keeps is the part that is genuinely its own: the local rules to validate
 * before anything is reserved, the roles the account is born with, and the business event that names
 * its row.
 *
 * <h2>Which events it emits, and which it does not</h2>
 *
 * <p>Everything that is about the <em>account</em> is emitted here, in both flows: {@code signup.started},
 * {@code signup.account.reserved}, {@code signup.email.duplicate}, {@code signup.compensation} and
 * {@code signup.activation.pending}. The two that name a profile row — {@code signup.completed} with its
 * {@code customerId}, {@code employee.created} with its {@code employeeId} — belong to the module that
 * owns the row and are emitted there. Both lists come from the closed registry of
 * {@code docs/11-observabilidad-y-logging.md} §3; nothing here invents a name.
 */
@Service
public class AccountRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(AccountRegistrationService.class);

    private static final String REGISTRATION_ID = "registrationId";
    private static final String ACCOUNT_ID = "accountId";

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

    private static final int ACTIVATION_ATTEMPTS = 3;

    private final AccountsPort accounts;

    AccountRegistrationService(AccountsPort accounts) {
        this.accounts = accounts;
    }

    /**
     * Runs the saga: reserve the email, write the profile, activate.
     *
     * @param command the account to reserve. Its {@code enabled} is expected to be {@code false} — a
     *     reservation that arrived enabled would be a live login for a profile that does not exist yet
     * @param localWrite the caller's single local transaction, run once the email is safely reserved
     * @throws EmailAlreadyExistsException propagated from ukun-auth, which the frontend paints under
     *     the email field. Nothing was written locally when this is thrown
     */
    public Registration register(CreateAccountCommand command, LocalProfileWriter localWrite) {
        // Step 1: the idempotency key of the whole saga. Issued here, sent with the reservation, and
        // repeated verbatim by any retry of this same registration — which is what makes the retry
        // return the same account instead of a second one.
        UUID registrationId = UUID.randomUUID();
        BusinessEvents.emit(
                Level.INFO, "signup.started", Map.of(REGISTRATION_ID, registrationId, "email", command.email()));

        AccountRef account = reserve(registrationId, command);

        long localId = writeOrRelease(registrationId, account, localWrite);

        return new Registration(registrationId, account.accountId(), localId, activate(registrationId, account));
    }

    /** Step 2: reserve the email with an account created disabled. */
    private AccountRef reserve(UUID registrationId, CreateAccountCommand command) {
        try {
            AccountRef account = accounts.reserve(registrationId, command);

            BusinessEvents.emit(
                    Level.INFO,
                    "signup.account.reserved",
                    Map.of(REGISTRATION_ID, registrationId, ACCOUNT_ID, account.accountId()));

            return account;

        } catch (EmailAlreadyExistsException taken) {
            // The email is the subject of this event, which is why it may carry one at all (docs/11 §4).
            BusinessEvents.emit(Level.INFO, "signup.email.duplicate", Map.of("email", command.email()));
            throw taken;
        }
    }

    /** Step 3: the single local transaction, with its compensation. */
    private long writeOrRelease(UUID registrationId, AccountRef account, LocalProfileWriter localWrite) {
        try {
            return localWrite.write(account.accountId());

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
                Map.of(REGISTRATION_ID, registrationId, ACCOUNT_ID, account.accountId(), "step", "register"));
    }

    /** Step 4: the commit point, retried, and the {@code 202} if it still does not land. */
    private RegistrationOutcome activate(UUID registrationId, AccountRef account) {
        try {
            ACTIVATION_RETRIES.execute(() -> {
                accounts.activate(account.accountId());
                return null;
            });

        } catch (RetryException exhausted) {
            BusinessEvents.emit(
                    Level.ERROR,
                    "signup.activation.pending",
                    Map.of(
                            REGISTRATION_ID,
                            registrationId,
                            ACCOUNT_ID,
                            account.accountId(),
                            "attempts",
                            ACTIVATION_ATTEMPTS));

            return RegistrationOutcome.ACTIVATION_PENDING;
        }

        return RegistrationOutcome.COMPLETED;
    }
}
