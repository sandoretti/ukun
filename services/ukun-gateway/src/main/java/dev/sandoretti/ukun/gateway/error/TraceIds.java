package dev.sandoretti.ukun.gateway.error;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * Resolves the {@code traceId} that every error response carries, the same way
 * {@code ukun-api} does — see the class of the same name in that service.
 *
 * <p>Since issue #30 the value normally comes from the MDC. This being a reactive service, that
 * depends on one property as much as on the dependency: {@code spring.reactor.context-propagation:
 * auto} in {@code application.yaml} is what makes Reactor restore the MDC around each signal, and
 * without it this class would keep inventing an identifier while a perfectly good trace travelled on
 * towards {@code ukun-api}. The reasoning is written where the property is.
 *
 * <p>The fallback to a fresh W3C-shaped identifier stays for whatever fails outside a traced
 * exchange: the client still gets a value it can quote to an operator, which is the whole point of
 * the field.
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
