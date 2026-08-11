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

    /**
     * The checkout attempt does not exist or its {@code expiresAt} has passed (ADR-0016). Extensions:
     * {@code attemptId} and {@code expiredAt}, the second one absent when there was never an attempt
     * to expire.
     *
     * <p>{@code 410} rather than {@code 404} because the frontend has to tell it from every other
     * missing resource: this one it recovers from on its own, by reopening the checkout with
     * {@code POST /api/checkout/attempts}.
     */
    CHECKOUT_EXPIRED(HttpStatus.GONE),

    /**
     * Some line of the order has no stock left in the online store (RF-04, ADR-0008). Extensions:
     * {@code stockLineId}, {@code productId}, {@code sku}, {@code available} and {@code requested} —
     * the first one null when the product has no stock row at all, which is a different fact from a
     * row sitting at zero and the only place in this service that says so.
     *
     * <p>It names <strong>one</strong> line, not all of them: the order is rejected whole (there are
     * no partial orders), and the customer needs to know which product to fix.
     */
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT),

    /**
     * Another request is already confirming this attempt (ADR-0016). Extension: {@code key}.
     *
     * <p>It is the honest answer to a double submit that arrived twice in the same instant: the first
     * request is still inside its transaction, so there is no stored response to replay yet and
     * inventing one would be guessing at an outcome that has not happened.
     */
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT),

    /**
     * The key was already used against a different cart (ADR-0016). Extension: {@code key}.
     *
     * <p>Normal use of the SPA cannot produce it — the attempt is issued by the server and sent
     * straight back — so it means somebody is reusing an {@code attemptId} for a different order, and
     * an explicit rejection beats serving whichever of the two answers came first.
     */
    IDEMPOTENCY_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT),

    /**
     * There is nothing in the cart to buy (RF-04). No extensions.
     *
     * <p>The legacy creates an order with no lines and a total of zero here. That is the bug this
     * code exists to refuse, and {@code ck_customer_order_total} makes it impossible to reintroduce
     * even if this check were removed.
     */
    EMPTY_CART(HttpStatus.UNPROCESSABLE_CONTENT),

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
