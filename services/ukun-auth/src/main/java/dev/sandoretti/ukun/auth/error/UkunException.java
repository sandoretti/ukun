package dev.sandoretti.ukun.auth.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Root of the typed exception hierarchy of {@code docs/12-error-contract.md} §3. Everything thrown to
 * say "this went wrong for a business reason" extends this, and the single
 * {@link GlobalExceptionHandler} renders any of them generically — no branch of the advice builds a
 * {@code ProblemDetail} field by field, so a new exception needs no change there.
 *
 * <p>{@link #getStatus()} is derived from the code rather than declared by each subclass: the registry
 * pins one status per code, so letting a subclass pick its own would be a way to answer {@code 404}
 * with a code the frontend has mapped to a {@code 409}.
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
