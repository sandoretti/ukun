package dev.sandoretti.ukun.gateway.error;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * The three codes of {@code docs/12-error-contract.md} §2 that <em>this</em> service emits. The
 * registry is closed and shared with {@code ukun-api}; what is not closed is how many of its rows a
 * given service can produce, and a gateway can produce very few.
 *
 * <h2>Why this is a copy and not a shared module</h2>
 *
 * ADR-0001 rules out a common module between the services, so the alternative to duplicating three
 * enum constants and thirty lines of {@link ProblemDetails} would be a library nobody asked for. The
 * duplication is bounded by design: the contract says the <em>shape</em> must match, and the shape
 * is four fields.
 *
 * <h2>Why only three</h2>
 *
 * {@link #PAYLOAD_TOO_LARGE} is the reason this class exists — {@code docs/12} §2 calls it the one
 * code of the registry with two possible emitters, because the gateway cuts an oversized upload
 * before it ever reaches {@code ukun-api}, and the frontend cannot tell which of the two answered.
 * {@link #UNAUTHENTICATED} and {@link #FORBIDDEN} come with the coarse role filter of ADR-0020: a
 * request rejected at the edge must look exactly like the same rejection made by the resource
 * server one hop later.
 *
 * <p>Nothing else belongs here. Adding a constant this service cannot emit would leave the frontend
 * a value to look up and nothing to produce it — the same reason {@code ukun-api} keeps its own copy
 * of the enum trimmed to what it actually throws.
 */
enum ErrorCode {

    /** No session yet. Same shape as the 401 of {@code ukun-api}, which is the point. */
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),

    /** Authenticated, but the role does not hold — {@code /api/admin/**} without {@code ADMIN}. */
    FORBIDDEN(HttpStatus.FORBIDDEN),

    /** Extension: {@code maxBytes}. {@code docs/08-identidad-y-seguridad.md} §9. */
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE);

    private static final String TYPE_PREFIX = "https://ukun.dev/errors/";

    private final HttpStatus status;
    private final URI type;
    private final String title;

    ErrorCode(HttpStatus status) {
        this.status = status;
        this.type = URI.create(TYPE_PREFIX + name().toLowerCase(Locale.ROOT).replace('_', '-'));
        this.title = titleOf(name());
    }

    HttpStatus status() {
        return status;
    }

    URI type() {
        return type;
    }

    String title() {
        return title;
    }

    /** {@code PAYLOAD_TOO_LARGE} to {@code "Payload too large"}. */
    private static String titleOf(String name) {
        String words = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
