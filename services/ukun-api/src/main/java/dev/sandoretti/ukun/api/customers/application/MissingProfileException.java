package dev.sandoretti.ukun.api.customers.application;

import java.util.UUID;

/**
 * A token carrying the {@code CUSTOMER} role with no {@code customer} row behind it.
 *
 * <p><strong>An {@link IllegalStateException} and deliberately not a {@code UkunException}.</strong>
 * The types of the closed registry ({@code docs/12-error-contract.md}) describe things the caller did;
 * this describes something this service got wrong. The signup saga activates an account only once its
 * profile exists ({@code docs/08-identidad-y-seguridad.md} §3), so there is no sequence of requests a
 * client can make that produces it. Giving it a {@code code} would publish a broken invariant as a
 * contract, and answering {@code 404} would tell the customer their profile is missing when what is
 * missing is a row somebody has to go and fix. It falls through to the advice's catch-all as a
 * {@code 500} with a stack trace in the log, which is what gets it looked at.
 *
 * <p>A type of its own because two use cases reach the same conclusion — {@code GET /api/me/profile}
 * and the {@code customerId} that {@code CustomersApi} resolves for the cart — and they used to say so
 * with the same sentence written out twice. The rule now has one place, and so does the reasoning.
 *
 * <p>The write side of the profile is not one of them: {@code PUT} answers {@code 404} for the same
 * situation, because a write declares that response in the contract and can therefore afford to be
 * precise about it. That contrast is deliberate and is explained where it happens.
 */
class MissingProfileException extends IllegalStateException {

    MissingProfileException(UUID accountId) {
        super("Account %s holds the CUSTOMER role with no profile behind it".formatted(accountId));
    }
}
