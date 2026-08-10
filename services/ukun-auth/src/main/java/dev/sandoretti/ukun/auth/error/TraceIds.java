package dev.sandoretti.ukun.auth.error;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * Resolves the {@code traceId} that every error response carries.
 *
 * <p>Since Micrometer Tracing landed in v0.5 ({@code docs/11-observabilidad-y-logging.md} §5) the
 * value normally comes from the MDC, filled from the {@code traceparent} of whoever called. In this
 * service that caller is {@code ukun-api} running the signup saga, and the trace is what joins a
 * refusal logged here to the {@code signup.compensation} logged there — see
 * {@code TraceCorrelationIT}.
 *
 * <p>The fallback to a fresh W3C-shaped identifier stays for a call that arrives without a trace. It
 * is the same value the advice logs the exception with, which is what makes the field useful to an
 * operator handed one.
 *
 * <p>It deliberately does not write the generated value back into the MDC: nothing removes it in a
 * {@code finally}, and a value left on a pooled thread would resurface under an unrelated request.
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
