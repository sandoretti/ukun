package dev.sandoretti.ukun.auth.error;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * Resolves the {@code traceId} that every error response carries.
 *
 * <p>The contract declares it required, but {@code docs/11-observabilidad-y-logging.md} places
 * Micrometer Tracing in <strong>v0.5</strong>, when a browser request first crosses three processes.
 * Until then there is no span and nothing in the MDC, so this falls back to a fresh W3C-shaped
 * identifier — the same value the advice logs the exception with, which is what makes the field
 * useful to an operator handed one. When v0.5 puts tracing on the classpath, Boot fills the MDC and
 * this starts returning the real trace with no change here.
 *
 * <p>It deliberately does not write the generated value back into the MDC: nothing clears it in a
 * {@code finally} yet, and a value left on a pooled thread would resurface under an unrelated
 * request.
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
