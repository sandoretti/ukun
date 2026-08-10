package dev.sandoretti.ukun.tracing;

import java.util.UUID;

/**
 * The W3C {@code traceparent} header, for the tests of the three services that assert correlation
 * ({@code docs/11-observabilidad-y-logging.md} §5).
 *
 * <p>It lives here for the same reason the ArchUnit rules do, and it is the same argument the build
 * file of this project makes about them: the three suites have to agree on this wire format down to
 * the sampling flag, and a copy per service is a guarantee that one of them will drift. A drifted
 * copy does not fail — it keeps passing while testing something else, which is the failure mode worth
 * paying a shared class to avoid.
 *
 * <p>Sampled ({@code -01}) always. An unsampled parent is a legitimate W3C value and it is precisely
 * what these tests must never send: a downstream service is entitled to drop the span, and the
 * assertion would then be about a decision nobody made on purpose.
 */
public record Traceparent(String traceId, String spanId) {

    /** The header name, spelled once. */
    public static final String HEADER = "traceparent";

    /** A trace id on its own, which is what a {@code ProblemDetail} carries. */
    public static final String TRACE_ID_PATTERN = "[0-9a-f]{32}";

    /** {@code version-traceid-spanid-flags}: 32 hex for the trace, 16 for the span. */
    public static final String HEADER_PATTERN = "^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$";

    public static Traceparent random() {
        String hex = randomHex();

        return new Traceparent(hex, randomHex().substring(0, 16));
    }

    /** The value to send as {@link #HEADER}. */
    public String header() {
        return "00-%s-%s-01".formatted(traceId, spanId);
    }

    private static String randomHex() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
