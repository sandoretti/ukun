package dev.sandoretti.ukun.auth.error;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * The closed registry of {@code docs/12-error-contract.md} §2, restricted to what <strong>this</strong>
 * service emits. The registry is single and shared with ukun-api so the frontend never has to work out
 * which service an error came from — but only a handful of its values can originate here, and listing
 * the rest would leave codes nothing is able to produce.
 *
 * <h2>Why this is a second copy and not a shared type</h2>
 *
 * ADR-0001: there is no module of shared production code, and this is exactly the kind of thing that
 * would go in one. The duplication is the price of not coupling the compilation of the two services,
 * and it is bounded — seven constants and the two mechanical derivations below, against a contract
 * that is frozen. The ADR reserves reopening the question for a genuinely shared fourth thing, not
 * for this.
 *
 * <p>{@code type} and {@code title} are derived rather than spelled out per row, exactly as in
 * ukun-api: {@code type} is {@code https://ukun.dev/errors/} plus the code in kebab-case, and
 * {@code title} is fixed English for the operator and the logs, never shown on screen (ADR-0019).
 */
public enum ErrorCode {

    /** Extension: {@code errors[]} with {@code field} and {@code message}. */
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),

    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),

    /** The client token is missing or invalid. Never says which. */
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),

    /** Authenticated as a client, but without the {@code accounts:write} scope. */
    FORBIDDEN(HttpStatus.FORBIDDEN),

    /** Extensions: {@code resource} and {@code id}. */
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),

    /**
     * The one code this service is the origin of. Uniqueness of the email is enforced here, by
     * {@code ux_account_email}, and travels to the browser propagated through the saga — which is why
     * the registry gives it a code of its own instead of folding it into {@code DUPLICATE_VALUE}
     * ({@code docs/12} §2).
     */
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),

    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE),

    /** Generic fallback. No detail ever reaches the client. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private static final String TYPE_PREFIX = "https://ukun.dev/errors/";

    /**
     * The code for a status Spring resolved on its own. It exists so a standard Spring MVC exception
     * nobody wrote a handler for still comes back with a {@code code} instead of a bare {@code 500} —
     * the defect measured and fixed in ukun-api's copy of this enum, not rediscovered here.
     */
    private static final Map<HttpStatus, ErrorCode> BY_STATUS = Map.of(
            HttpStatus.BAD_REQUEST, VALIDATION_FAILED,
            HttpStatus.UNAUTHORIZED, UNAUTHENTICATED,
            HttpStatus.FORBIDDEN, FORBIDDEN,
            HttpStatus.NOT_FOUND, RESOURCE_NOT_FOUND,
            HttpStatus.METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED,
            HttpStatus.NOT_ACCEPTABLE, NOT_ACCEPTABLE);

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
