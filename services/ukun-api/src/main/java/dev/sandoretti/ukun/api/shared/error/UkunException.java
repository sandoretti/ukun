package dev.sandoretti.ukun.api.shared.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Root of the typed exception hierarchy of {@code docs/12-error-contract.md} §3. Everything a service
 * throws to say "this went wrong for a business reason" extends this, and the single
 * {@link GlobalExceptionHandler} renders any of them generically — no branch of the advice builds a
 * {@code ProblemDetail} field by field.
 *
 * <p>It is also the other half of the "a service never returns {@code null}" rule (skill
 * {@code spring-api}, rule 5): the alternative to a null return is an {@code Optional} or one of
 * these, never a null the caller has to test for.
 *
 * <p>{@link #getStatus()} is derived from the code rather than declared by each subclass on purpose.
 * The registry pins one status per code, so letting a subclass choose its own would be a way to
 * answer {@code 404} with a code the frontend has mapped to a {@code 409}.
 */
public abstract class UkunException extends RuntimeException {

    protected UkunException(String message) {
        super(message);
    }

    public abstract ErrorCode getCode();

    public final HttpStatus getStatus() {
        return getCode().status();
    }

    /** The code-specific fields of the registry. Empty when the code declares none. */
    public Map<String, Object> getExtensions() {
        return Map.of();
    }
}
