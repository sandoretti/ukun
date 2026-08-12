package dev.sandoretti.ukun.api.shared.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.event.Level;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The one place that knows what {@code auth.authorization.denied} looks like
 * ({@code docs/11-observabilidad-y-logging.md} §3).
 *
 * <p>It has <strong>two publishers and one shape</strong>, which is the reason it exists. The role
 * half is emitted by {@code identity}'s listener, from the event Spring Security publishes; the
 * {@code FOREIGN_RESOURCE} half by the use case that decides it, because no framework event is
 * published for a domain denial. Written out twice, the two would drift the first time one of them was
 * fixed — a renamed field, a different value for {@code reason}, method and path missing from one of
 * them — and the audit would then answer differently depending on which check turned the caller away.
 *
 * <p>It lives in {@code shared} because both publishers are in different modules and neither owns the
 * event. Nothing of Spring Security or of the domain reaches in here: the caller passes the values it
 * already holds, so this class stays free of any module's model.
 */
public final class AuthorizationDenied {

    private static final String EVENT = "auth.authorization.denied";

    private AuthorizationDenied() {}

    /**
     * Emits the denial, adding the method and path of the request in flight.
     *
     * @param accountId the {@code sub} of the token. Never the email: it is PII, and this event is not
     *     among the three allowed to carry one ({@code docs/11} §5).
     * @param roles what the caller does hold, which is the useful half of "why was this denied"
     */
    public static void emit(Object accountId, String roles, Reason reason) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("accountId", accountId);
        fields.put("role", roles);
        fields.putAll(currentRequest());
        fields.put("reason", reason.name());

        BusinessEvents.emit(Level.WARN, EVENT, fields);
    }

    /**
     * Method and path come from the request rather than from whatever object the denial carried: a
     * method rule holds a {@code MethodInvocation} and a route rule holds a request, and the audit
     * wants the same two fields either way.
     */
    private static Map<String, Object> currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Map.of();
        }

        return Map.of(
                "method", attributes.getRequest().getMethod(),
                "path", attributes.getRequest().getRequestURI());
    }

    /**
     * The two values {@code docs/11} §3 lists for this field, as a type rather than as two literals in
     * two modules.
     */
    public enum Reason {

        /** A rule about what the caller <em>is</em>: a role they do not hold. */
        ROLE,

        /** A rule about what the caller <em>owns</em>: somebody else's store, somebody else's order. */
        FOREIGN_RESOURCE
    }
}
