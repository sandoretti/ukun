package dev.sandoretti.ukun.api.shared.error;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * Resolves the {@code traceId} that every error response carries.
 *
 * <p>Since Micrometer Tracing landed in v0.5 ({@code docs/11-observabilidad-y-logging.md} §5) the
 * usual answer comes from the MDC, which Boot fills from the {@code traceparent} the gateway sends —
 * so the value in this response is the same one that identifies the request in the logs of all three
 * services. That is the whole point of the field, and {@code TraceCorrelationIT} is what says it is
 * true rather than intended.
 *
 * <p>The fallback to a fresh W3C-shaped identifier stays, for the case that still exists: this
 * service listens on its own port and can be called without a trace, or fail before one is
 * established. An operator handed a {@code traceId} can still find the line the advice logged with
 * it — the two always match, because both come from this single read.
 *
 * <p>It deliberately does <strong>not</strong> write the generated value back into the MDC. Only the
 * key that a filter removes in a {@code finally} may be written there ({@code docs/11} §5, and see
 * {@code shared.observability.AccountIdMdcFilter}); a value left behind on a pooled thread would
 * resurface on the next request under a different one, and two unrelated requests reported under the
 * same trace is worse than no trace at all.
 */
final class TraceIds {

    private static final String MDC_KEY = "traceId";

    private TraceIds() {}

    static String current() {
        String active = MDC.get(MDC_KEY);
        if (active != null && !active.isBlank()) {
            return active;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
