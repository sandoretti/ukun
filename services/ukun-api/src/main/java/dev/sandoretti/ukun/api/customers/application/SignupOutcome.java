package dev.sandoretti.ukun.api.customers.application;

/**
 * How far a signup got, which is the difference between the {@code 201} and the {@code 202} of
 * {@code POST /api/signup}.
 *
 * <p>Two values and not a boolean, because the pair is not "worked / failed": a failure is an
 * exception. Both of these are successes in which the customer exists and their data is safe; they
 * differ only in whether the account is usable yet.
 */
public enum SignupOutcome {

    /** Account activated. The frontend's next step is the login. */
    COMPLETED,

    /**
     * The customer was created but the activation could not be confirmed. Repeating the same signup
     * activates the account, and nothing is duplicated — the reservation is keyed by an idempotency
     * key and the email is already taken by this very account.
     *
     * <p>It is deliberately not an error. The person's data is written and their money has not been
     * taken; telling them "something went wrong" would invite them to sign up again with a different
     * email, which is the one outcome nobody wants.
     */
    ACTIVATION_PENDING
}
