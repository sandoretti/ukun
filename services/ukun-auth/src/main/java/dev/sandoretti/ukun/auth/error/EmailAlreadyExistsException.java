package dev.sandoretti.ukun.auth.error;

/**
 * The email belongs to another account. This service is the <em>origin</em> of the code, not a relay
 * of it: uniqueness lives in {@code ux_account_email}, and ukun-api propagates the {@code 409} to the
 * browser as it arrives ({@code docs/08-identidad-y-seguridad.md} §3).
 *
 * <p>It is raised from a constraint violation rather than from a preceding {@code existsByEmail}.
 * That is the whole correction: the legacy {@code ClienteValidator} asks and then saves, with a real
 * race between the two calls, and the answer to losing that race is this exception instead of a
 * duplicate row.
 *
 * <p>The message carries no email. The text reaches the log, and an address on every line is PII this
 * service has no reason to spread ({@code docs/11-observabilidad-y-logging.md} §4).
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
