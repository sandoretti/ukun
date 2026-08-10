package dev.sandoretti.ukun.gateway.error;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * Resolves the {@code traceId} that every error response carries, the same way
 * {@code ukun-api} does — see the class of the same name in that service.
 *
 * <p>The contract declares the field required, and Micrometer Tracing lands in this same release but
 * in a different slice (issue #30). Until it does there is no span and therefore nothing in the MDC,
 * so this falls back to a fresh W3C-shaped identifier: the client still gets a value it can quote to
 * an operator, which is the whole point of the field.
 *
 * <p>Reading the MDC in a reactive service is not the oversight it looks like. When #30 puts
 * {@code micrometer-tracing} on the classpath, Boot enables Reactor's automatic context propagation,
 * which is precisely what restores the MDC around each signal — so this class starts returning the
 * real trace with no change here, exactly as in {@code ukun-api}.
 *
 * <p>It deliberately does <strong>not</strong> write the generated value back into the MDC: a value
 * left behind on an event-loop thread would resurface on an unrelated request, and two requests
 * reported under the same trace is worse than no trace at all.
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
