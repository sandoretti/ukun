package dev.sandoretti.ukun.auth.error;

import java.util.List;
import java.util.Map;

/**
 * The password does not meet the policy of {@code docs/08-identidad-y-seguridad.md} §8.
 *
 * <p>It reports {@code VALIDATION_FAILED} with an {@code errors[]} entry on {@code password}, and
 * <strong>not</strong> a code of its own. The registry of {@code docs/12-error-contract.md} is closed
 * and has no {@code WEAK_PASSWORD}; inventing one here would be a value the frontend cannot translate
 * and the audit does not know. What the user sees is decided by the frontend from the field and the
 * code, exactly as with any other rejected field.
 *
 * <p>The reason is deliberately coarse — too short, or known to be compromised — and never echoes the
 * password or any part of it. Confirming that a specific string appears in a breach corpus is
 * information about the string, and this response is not the place to hand it out.
 */
public final class WeakPasswordException extends UkunException {

    private static final String FIELD = "password";

    private final String reason;

    private WeakPasswordException(String reason) {
        super("The password does not meet the policy: " + reason);
        this.reason = reason;
    }

    /** Shorter than the minimum of §8. The bound is stated so an operator can read the log line. */
    public static WeakPasswordException tooShort(int minimumLength) {
        return new WeakPasswordException("must be at least %d characters long".formatted(minimumLength));
    }

    /**
     * Past the limit of the hashing algorithm. It is reported like any other rejected field rather
     * than as an internal error, which is what it looked like before: BCrypt refuses to encode more
     * than 72 bytes, so without this the request died as a {@code 500} with a stack trace.
     */
    public static WeakPasswordException tooLong(int maximumBytes) {
        return new WeakPasswordException("must be at most %d bytes long".formatted(maximumBytes));
    }

    /** Present in a known breach corpus — the check §8 asks for, k-anonymised. */
    public static WeakPasswordException compromised() {
        return new WeakPasswordException("appears in a known data breach");
    }

    @Override
    public ErrorCode getCode() {
        return ErrorCode.VALIDATION_FAILED;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return Map.of("errors", List.of(new ValidationError(FIELD, reason)));
    }
}
