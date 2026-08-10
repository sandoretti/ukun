package dev.sandoretti.ukun.api.shared.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;

/**
 * The single emitter of business events of this service, {@code docs/11-observabilidad-y-logging.md}
 * §6 — the counterpart of the class ukun-auth already has. One logger of its own so its level can be
 * tuned without touching the rest of the tree: {@code logging.level.ukun.business=INFO} survives
 * everything else dropping to WARN, which is what makes "only what affects the user" a configuration
 * rather than a promise.
 *
 * <p>The event name always comes from the <strong>closed</strong> table of {@code docs/11} §3.
 * Inventing one breaks the audit silently, which is the failure that registry exists to prevent.
 *
 * <p>It lives in {@code shared} rather than in the module that first needed it ({@code identity},
 * with {@code auth.authorization.denied}) because every module emits events and none of them owns
 * the mechanism — {@code shared} is exactly the "cross-cutting building blocks" module of ADR-0017
 * §1.
 */
public final class BusinessEvents {

    private static final Logger log = LoggerFactory.getLogger("ukun.business");

    private BusinessEvents() {}

    /**
     * Fields go through the MDC rather than into the message, so a structured appender can index
     * them and a human still reads one line. They are always removed afterwards: the thread is
     * pooled, and a leftover {@code accountId} would attach itself to somebody else's request.
     *
     * <p>What must never be passed here is spelled out in {@code docs/11} §4: no entity, no
     * password, no token, no card number, and no {@code email} outside the three events where it is
     * the subject.
     */
    public static void emit(Level level, String event, Map<String, Object> fields) {
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
