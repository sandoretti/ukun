package dev.sandoretti.ukun.api.identity;

import dev.sandoretti.ukun.api.shared.error.ErrorCode;
import dev.sandoretti.ukun.api.shared.error.UkunException;

/**
 * The email belongs to another account. Raised by the adapter when ukun-auth answers {@code 409} to a
 * reservation, and propagated to the client as it is: the frontend paints it under the email field of
 * the signup form ({@code docs/08-identidad-y-seguridad.md} §3).
 *
 * <p>It replaces the legacy {@code ClienteValidator}, which asked {@code existsByCuenta_Correo} and
 * then saved — with a real race between the two calls. Here uniqueness is decided by the database
 * that owns the constraint, and the answer to losing the race is this exception rather than a
 * duplicate row.
 *
 * <p>The message carries no email: the exception's text reaches the log, and an address in every line
 * is PII this service has no reason to spread ({@code docs/11-observabilidad-y-logging.md} §4).
 */
public final class EmailAlreadyExistsException extends UkunException {

    public EmailAlreadyExistsException() {
        super("The email is already in use by another account");
    }

    @Override
    public ErrorCode getCode() {
        return ErrorCode.EMAIL_ALREADY_EXISTS;
    }
}
