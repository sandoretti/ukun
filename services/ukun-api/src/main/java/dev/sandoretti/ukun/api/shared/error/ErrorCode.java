package dev.sandoretti.ukun.api.shared.error;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * The closed registry of error codes from {@code docs/12-error-contract.md} §2, restricted to the five
 * this release actually emits. It is <strong>not</strong> the whole table: the {@code Release} column
 * says when each code is emitted for the first time, and adding one before its endpoint exists would
 * leave a value the frontend can look up and nothing can produce.
 *
 * <p>A new value enters through the four steps of §4, in that order — registry, OpenAPI, code,
 * {@code errors.json} of both languages — never by adding a constant here first.
 *
 * <p>{@code type} and {@code title} are derived mechanically rather than spelled out per row, which
 * is exactly what §2 says at the end: {@code type} is {@code https://ukun.dev/errors/} plus the code
 * in kebab-case. {@code title} is fixed English aimed at the operator and the logs, and is never
 * shown on screen — user-facing text lives in the frontend, keyed by the code (ADR-0019).
 */
public enum ErrorCode {

    /** Extension: {@code errors[]} with {@code field} and {@code message}. */
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),

    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),

    /** Extensions: {@code resource} and {@code id}. */
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),

    REFERENTIAL_INTEGRITY(HttpStatus.CONFLICT),

    /** Generic fallback. No detail ever reaches the client. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private static final String TYPE_PREFIX = "https://ukun.dev/errors/";

    private final HttpStatus status;
    private final URI type;
    private final String title;

    ErrorCode(HttpStatus status) {
        this.status = status;
        this.type = URI.create(TYPE_PREFIX + name().toLowerCase(Locale.ROOT).replace('_', '-'));
        this.title = titleOf(name());
    }

    public HttpStatus status() {
        return status;
    }

    public URI type() {
        return type;
    }

    public String title() {
        return title;
    }

    /** {@code VALIDATION_FAILED} to {@code "Validation failed"}. */
    private static String titleOf(String name) {
        String words = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
