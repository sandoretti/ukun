package dev.sandoretti.ukun.api.identity;

import java.util.UUID;

/**
 * The way out to ukun-auth, and one of the two external boundaries of this service — the other being
 * {@code FileStoragePort} (ADR-0017 §1, {@code docs/03-analisis-y-patrones.md} §3). It is an interface
 * so the signup saga can be tested against a double instead of a running Authorization Server: the
 * {@code testing} skill allows mocking exactly here and nowhere deeper.
 *
 * <p>The three operations are the three of {@code docs/api/openapi/ukun-auth.yaml}, in the order the
 * saga of {@code docs/08-identidad-y-seguridad.md} §3 uses them: reserve the email, write locally,
 * then activate. <strong>Orchestrating them is not this module's job</strong> — the saga belongs to
 * whoever owns signup; this port only knows how to speak to the other service.
 *
 * <p>Everything here goes over {@code client_credentials} with the {@code accounts:write} scope,
 * inside the container network. No browser ever takes part in this flow.
 */
public interface AccountsPort {

    /**
     * Step 2 of the saga: reserves the email with an account created <strong>disabled</strong>, before
     * this service writes anything of its own.
     *
     * <p>The order is what makes the saga correct without distributed transactions: email uniqueness
     * is the one rule that cannot be checked locally, so it is checked first and an orphan customer
     * never happens.
     *
     * @param registrationId the {@code Idempotency-Key}. Issued by this service and reused verbatim on
     *     every retry of the same signup — that is what makes a retry return the same account instead
     *     of a second one.
     * @throws EmailAlreadyExistsException if another account already holds the email. An account
     *     reserved by a <em>different</em> attempt cannot be taken over.
     */
    AccountRef reserve(UUID registrationId, CreateAccountCommand command);

    /**
     * Step 4, and the real commit point of a signup: until this succeeds the account allows no login.
     * Idempotent — activating an already active account is a success, not a conflict.
     */
    void activate(UUID accountId);

    /**
     * Compensation for a local transaction that failed after the reservation. Best-effort by design:
     * the daily reconciliation job in ukun-auth is what guarantees no orphan accounts, so this call
     * failing does not compromise correctness — it only frees the email sooner in the usual case.
     */
    void release(UUID accountId);
}
