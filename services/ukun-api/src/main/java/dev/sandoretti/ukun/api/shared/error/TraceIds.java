package dev.sandoretti.ukun.api.shared.error;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * Resolves the {@code traceId} that every error response carries.
 *
 * <p>The contract declares it required in {@code ProblemDetail}, but
 * {@code docs/11-observabilidad-y-logging.md} places Micrometer Tracing in <strong>v0.5</strong>, when
 * a browser request first crosses three processes — instrumenting a hop that does not exist yet is
 * cost without signal. So today there is no span, and therefore nothing in the MDC.
 *
 * <p>This reads the MDC anyway and falls back to a fresh W3C-shaped identifier. Two consequences,
 * both deliberate:
 *
 * <ul>
 *   <li>The value the client receives is the same one the advice logs the exception with, so an
 *       operator handed a {@code traceId} can still find the stack trace — which is the whole point
 *       of the field.
 *   <li>When v0.5 puts Micrometer Tracing on the classpath, Boot populates the MDC on its own and
 *       this class starts returning the real trace with no change here.
 * </ul>
 *
 * <p>It deliberately does <strong>not</strong> write the generated value back into the MDC. There is
 * no servlet filter clearing it in a {@code finally} yet ({@code docs/11} §"MDC"), and a value left
 * behind on a pooled thread would resurface on the next request under a different one — two
 * unrelated requests reported under the same trace is worse than no trace at all.
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
