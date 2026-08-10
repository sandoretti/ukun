package dev.sandoretti.ukun.api.shared.error;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * The closed registry of error codes from {@code docs/12-error-contract.md} §2, restricted to the
 * ones this release actually emits. It is <strong>not</strong> the whole table: the {@code Release}
 * column says when each code is emitted for the first time, and adding one before its endpoint
 * exists would leave a value the frontend can look up and nothing can produce.
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

    /** The token is missing, expired, or was not issued for this service. Never says which. */
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),

    /** Authenticated, but not allowed: the wrong role, or somebody else's resource. */
    FORBIDDEN(HttpStatus.FORBIDDEN),

    /** Extensions: {@code resource} and {@code id}. */
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),

    /**
     * Uniqueness of an email, which this service cannot check locally — it belongs to ukun-auth and
     * arrives propagated through the signup saga. That is why it has a code of its own instead of
     * being folded into {@code DUPLICATE_VALUE} ({@code docs/12} §2, "three boundaries").
     */
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),

    /** Extension: {@code allowed} — the methods the resource does accept. */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    /** Extension: {@code allowed} — the media types this service can produce. */
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE),

    REFERENTIAL_INTEGRITY(HttpStatus.CONFLICT),

    /** Generic fallback. No detail ever reaches the client. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private static final String TYPE_PREFIX = "https://ukun.dev/errors/";

    /**
     * The code that answers a status Spring resolved on its own, for the statuses the registry
     * covers. It exists so a standard Spring MVC exception nobody wrote a handler for still comes
     * back with a {@code code} instead of being flattened into a {@code 500} — which is exactly the
     * defect this map was introduced to fix.
     */
    private static final Map<HttpStatus, ErrorCode> BY_STATUS = Map.of(
            HttpStatus.BAD_REQUEST, VALIDATION_FAILED,
            HttpStatus.UNAUTHORIZED, UNAUTHENTICATED,
            HttpStatus.FORBIDDEN, FORBIDDEN,
            HttpStatus.NOT_FOUND, RESOURCE_NOT_FOUND,
            HttpStatus.METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED,
            HttpStatus.NOT_ACCEPTABLE, NOT_ACCEPTABLE,
            HttpStatus.CONFLICT, REFERENTIAL_INTEGRITY);

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

    /** {@link #INTERNAL_ERROR} for anything the registry does not cover, never an invented value. */
    static ErrorCode forStatus(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status == null ? INTERNAL_ERROR : BY_STATUS.getOrDefault(status, INTERNAL_ERROR);
    }

    /** {@code VALIDATION_FAILED} to {@code "Validation failed"}. */
    private static String titleOf(String name) {
        String words = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
