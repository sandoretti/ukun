package dev.sandoretti.ukun.gateway.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;

/**
 * The single emitter of business events, {@code docs/11-observabilidad-y-logging.md} §6. One logger
 * of its own so its level can be tuned without touching the rest of the tree:
 * {@code logging.level.ukun.business=INFO} survives everything else dropping to WARN.
 *
 * <p>The event name always comes from the <strong>closed</strong> table of {@code docs/11} §3. This
 * service emits exactly one row of it — {@code auth.logout} — because logout is the only step of the
 * authentication flow that happens <em>here</em> rather than in {@code ukun-auth}: the gateway owns
 * the browser session, so it is the only party that can observe it ending.
 *
 * <h2>Why this is a copy</h2>
 *
 * {@code ukun-auth} and {@code ukun-api} each have their own, for the reason ADR-0001 gives: there is
 * no common module in this repo, and a shared library for twenty lines would be a worse trade than
 * the duplication. The <em>registry</em> is shared; the emitter is not.
 *
 * <h2>The MDC in a reactive service</h2>
 *
 * Safe here for a narrow reason worth stating, because the general pattern is not: the put, the log
 * and the remove happen in one synchronous block with no operator in between, so no signal can hop
 * threads while a key is set. That is the same care {@code error/TraceIds} documents from the reading
 * side — and the reason neither class ever leaves a value behind on an event-loop thread, where it
 * would resurface attached to an unrelated request.
 */
final class BusinessEvents {

    private static final Logger log = LoggerFactory.getLogger("ukun.business");

    private BusinessEvents() {}

    static void emit(Level level, String event, Map<String, Object> fields) {
        Map<String, String> stamped = new LinkedHashMap<>();
        fields.forEach((key, value) -> {
            if (value != null) {
                stamped.put(key, String.valueOf(value));
                MDC.put(key, stamped.get(key));
            }
        });

        try {
            log.atLevel(level).log("{} {}", event, stamped);
        } finally {
            stamped.keySet().forEach(MDC::remove);
        }
    }
}
