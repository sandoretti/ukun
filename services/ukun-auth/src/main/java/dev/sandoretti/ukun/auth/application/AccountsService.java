package dev.sandoretti.ukun.auth.application;

import dev.sandoretti.ukun.auth.domain.Account;
import dev.sandoretti.ukun.auth.domain.RegistrationRequest;
import dev.sandoretti.ukun.auth.domain.Role;
import dev.sandoretti.ukun.auth.error.AccountNotFoundException;
import dev.sandoretti.ukun.auth.error.EmailAlreadyExistsException;
import dev.sandoretti.ukun.auth.infra.AccountRepository;
import dev.sandoretti.ukun.auth.infra.RegistrationRequestRepository;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads accounts, and is the reason {@link Account} never leaves this layer: the mapping to
 * {@link AccountCredentials} happens here, inside the transaction, so no lazy association can escape
 * into a caller that has no session.
 *
 * <p>That property is <strong>enforced</strong>, not merely intended — the fourth of the seven
 * ArchUnit rules of {@code docs/03-analisis-y-patrones.md} §3 fails the build if a public method of a
 * {@code @Service} returns an {@code @Entity}. It was never Modulith's doing, which is part of why
 * ADR-0022 could remove it without losing this.
 *
 * <p>A concrete class and not an interface with one implementation: it had one as long as it was the
 * public API of a Modulith module, and without modules there is nothing left for it to encapsulate.
 * The signup saga's reserve/activate/release live here for the same reason, rather than on a second
 * type parallel to this one — an account is an account whoever is asking.
 *
 * <p>{@code @Transactional(readOnly = true)} at class level, per the skill {@code spring-api}: a
 * read-only transaction is the default and a writing method opts out explicitly, rather than the
 * other way round.
 */
@Service
@Transactional(readOnly = true)
public class AccountsService {

    private final AccountRepository accounts;
    private final RegistrationRequestRepository registrations;
    private final AccountReservations reservations;
    private final PasswordPolicy passwordPolicy;

    AccountsService(
            AccountRepository accounts,
            RegistrationRequestRepository registrations,
            AccountReservations reservations,
            PasswordPolicy passwordPolicy) {
        this.accounts = accounts;
        this.registrations = registrations;
        this.reservations = reservations;
        this.passwordPolicy = passwordPolicy;
    }

    /**
     * Looks an account up by its login credential. Case-insensitivity is not implemented here: the
     * column is {@code CITEXT}, so the guarantee lives in the type and no caller can forget it
     * (docs/10-modelo-de-datos.md §2).
     *
     * <p>Returns the account whether or not it is enabled. Deciding what a disabled account means is
     * authentication's job — and answering "no such account" for a reserved one would make the two
     * indistinguishable to the code that has to log the difference ({@code reason=CREDENTIALS} vs
     * {@code DISABLED}, docs/08 §8).
     */
    public Optional<AccountCredentials> findCredentialsByEmail(String email) {
        return accounts.findByEmail(email).map(AccountsService::toCredentials);
    }

    /**
     * Step 2 of the signup saga ({@code docs/08-identidad-y-seguridad.md} §3): reserves {@code email}
     * with an account created <strong>disabled</strong>, before ukun-api writes anything of its own.
     *
     * <p>Reserving first is what makes the saga correct without distributed transactions. Email
     * uniqueness is the one rule ukun-api cannot check in its own database, so it is checked here,
     * first, and an orphan customer never happens.
     *
     * <h2>Idempotency, and the two races it has to survive</h2>
     *
     * <p>The happy replay is the cheap one: the key is already in {@code registration_request}, so the
     * same {@code accountId} comes back with {@code created = false} and nothing is written.
     *
     * <p>The interesting case is two calls in flight at once, where both find nothing and both try to
     * insert. Exactly one wins, and the loser gets a constraint violation that could be either of two
     * things — and they need opposite answers:
     *
     * <ul>
     *   <li>{@code pk_registration_request}: the winner carried the <em>same</em> key. This is the
     *       same signup retried, so the right answer is the winner's account, not an error.
     *   <li>{@code ux_account_email}: the winner carried a different key. The email is taken by
     *       another attempt, and an account reserved by one attempt is not handed to another —
     *       {@code 409}.
     * </ul>
     *
     * <p>They are told apart by re-reading the key rather than by parsing the constraint name out of
     * the exception. The re-read answers the question that actually matters ("is there now a
     * reservation under my key?") and does not depend on the driver's message text.
     *
     * <p>{@code NOT_SUPPORTED} suspends the class-level read-only transaction for the whole method.
     * Without it the write would join a read-only transaction, and the recovery read would run inside
     * the transaction the failed insert had already marked rollback-only.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ReservedAccount reserve(UUID idempotencyKey, String email, String rawPassword, Set<Role> roles) {
        Optional<UUID> replayed = accountReservedUnder(idempotencyKey);
        if (replayed.isPresent()) {
            return new ReservedAccount(replayed.get(), false);
        }

        // Before the insert, so a rejected password costs no write and no BCrypt round.
        passwordPolicy.validate(rawPassword);

        try {
            return new ReservedAccount(reservations.insert(idempotencyKey, email, rawPassword, roles), true);
        } catch (DataIntegrityViolationException collision) {
            return accountReservedUnder(idempotencyKey)
                    .map(accountId -> new ReservedAccount(accountId, false))
                    .orElseThrow(EmailAlreadyExistsException::new);
        }
    }

    /**
     * Step 4 of the saga, and the real commit point of a signup: until this succeeds the account
     * allows no login.
     *
     * <p>Idempotent, as the contract declares — activating an already active account is a success.
     * That is not leniency: ukun-api retries this call with backoff, and answering an error on the
     * second attempt would turn a delivered activation into a failed signup.
     *
     * @throws AccountNotFoundException if no such account exists. Unlike a repeated activation, this
     *     one is a real failure: the saga is committing something that was never reserved.
     */
    @Transactional
    public void activate(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        account.setEnabled(true);
    }

    /**
     * Compensation for a local transaction of ukun-api that failed after the reservation. Deleting the
     * account frees the email straight away, and {@code ON DELETE CASCADE} takes its roles and its
     * idempotency key with it (baseline, changeset {@code 001-10}).
     *
     * <p><strong>It is not the guarantee that no orphan accounts are left</strong> — the reconciliation
     * job is ({@link AccountReconciliationJob}). This is the fast path for the usual case, which is why
     * an account that is already gone is a success and not a {@code 404}: the caller wants the email
     * free, and it is.
     *
     * <p>It does not check {@code enabled}. A caller with the {@code accounts:write} scope asking to
     * release an account it has just reserved is inside its own saga, and second-guessing that here
     * would only make the compensation fail in the case it exists for.
     */
    @Transactional
    public void release(UUID accountId) {
        accounts.deleteById(accountId);
    }

    /** The account a previous call reserved under {@code idempotencyKey}, if there was one. */
    private Optional<UUID> accountReservedUnder(UUID idempotencyKey) {
        return registrations.findById(idempotencyKey).map(RegistrationRequest::getAccountId);
    }

    private static AccountCredentials toCredentials(Account account) {
        return new AccountCredentials(
                account.getId(),
                account.getEmail(),
                account.getPasswordHash(),
                account.isEnabled(),
                new LinkedHashSet<>(account.getRoles()));
    }
}
