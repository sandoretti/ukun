package dev.sandoretti.ukun.auth.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;

/**
 * The single emitter of business events, {@code docs/11-observabilidad-y-logging.md} §6. One logger
 * of its own so its level can be tuned without touching the rest of the tree:
 * {@code logging.level.ukun.business=INFO} survives everything else dropping to WARN, which is what
 * makes "only what affects the user" a configuration rather than a promise.
 *
 * <p>The event name always comes from the <strong>closed</strong> table of {@code docs/11} §3. There
 * is no method here that takes an arbitrary string by accident: inventing a name breaks the audit
 * silently, which is the failure this registry exists to prevent.
 *
 * <p>It lives in {@code oauth2} because every event this service emits today is an {@code auth.*}
 * one. When issue #25 adds the {@code signup.*} half in {@code registration}, this class moves to a
 * place both can see — not before, because a shared utility with one user is a guess about the
 * second.
 */
final class BusinessEvents {

    private static final Logger log = LoggerFactory.getLogger("ukun.business");

    private BusinessEvents() {}

    /**
     * Fields go through the MDC rather than into the message, so a structured appender can index
     * them and a human still reads one line. They are always removed afterwards: the thread is
     * pooled, and a leftover {@code accountId} would attach itself to somebody else's request.
     */
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
