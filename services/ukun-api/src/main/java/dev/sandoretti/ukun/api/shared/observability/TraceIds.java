package dev.sandoretti.ukun.api.shared.observability;

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
 * {@link AccountIdMdcFilter}); a value left behind on a pooled thread would resurface on the next
 * request under a different one, and two unrelated requests reported under the same trace is worse
 * than no trace at all.
 *
 * <p>It lives here, next to {@link AccountIdMdcFilter}, rather than in {@code shared.error} where it
 * started: this is the package that owns the MDC keys of {@code docs/11} §5, and since v0.7 the trail
 * of ADR-0014 reads the same value for {@code audit_log.trace_id}. One reader for one key — the
 * alternative was a second class in {@code audit} spelling {@code "traceId"} out again.
 */
public final class TraceIds {

    private static final String MDC_KEY = "traceId";

    private TraceIds() {}

    /**
     * The trace of this request, or a fresh W3C-shaped identifier when there is none — for an error
     * response, which must carry the field whatever happened.
     */
    public static String current() {
        String active = currentOrNull();

        return active != null ? active : UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * The trace of this request, or {@code null}.
     *
     * <p>What the audit trail needs, and the reason the fallback of {@link #current()} is not shared:
     * {@code audit_log.trace_id} is nullable precisely so that a row written outside a traced request
     * says so. Storing an invented identifier there would promise a log line that does not exist, and
     * the column is the bridge to that line ({@code docs/11} §2).
     */
    public static String currentOrNull() {
        String active = MDC.get(MDC_KEY);

        return active != null && !active.isBlank() ? active : null;
    }
}
