package dev.sandoretti.ukun.api.identity;

/**
 * How far a registration got, which is the difference between the {@code 201} and the {@code 202} of
 * both {@code POST /api/signup} and {@code POST /api/admin/employees}.
 *
 * <p>Two values and not a boolean, because the pair is not "worked / failed": a failure is an
 * exception. Both of these are successes in which the person exists and their data is safe; they
 * differ only in whether the account is usable yet.
 */
public enum RegistrationOutcome {

    /** Account activated. The next step is the login. */
    COMPLETED,

    /**
     * The profile was created but the activation could not be confirmed. Repeating the same
     * registration activates the account, and nothing is duplicated — the reservation is keyed by an
     * idempotency key and the email is already taken by this very account.
     *
     * <p>It is deliberately not an error. The person's data is written and nothing was charged;
     * answering "something went wrong" would invite a second attempt with a different email, which is
     * the one outcome nobody wants.
     */
    ACTIVATION_PENDING
}
