package dev.sandoretti.ukun.api.identity.internal;

import dev.sandoretti.ukun.api.shared.observability.BusinessEvents;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.event.Level;
import org.springframework.context.event.EventListener;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Emits {@code auth.authorization.denied} — the role half of it — from the event Spring Security
 * already publishes when a rule turns a request away.
 *
 * <p>Listening to that event rather than writing the log line where the denial is decided is the same
 * choice {@code docs/11-observabilidad-y-logging.md} §3 makes for ukun-auth's login events, and for
 * the same reason: the place where the outcome is known is the place that cannot forget to report it.
 * A denial added by a future {@code @PreAuthorize} is audited without anybody remembering to audit it.
 *
 * <p><strong>An anonymous caller produces no event.</strong> Every request to a protected route
 * without a token is denied here first, so listening indiscriminately would turn one port scan into a
 * flood of WARN lines about nobody — while what this event is for is the case that matters: a real,
 * identified account reaching for something it may not have. The anonymous case is already a
 * {@code 401} in the access log.
 *
 * <p>The other half of the event, {@code reason=FOREIGN_RESOURCE} ("this store is not yours"), has no
 * {@code AuthorizationDeniedEvent} to hang from — it is a domain decision — and is emitted from the
 * {@code @RestControllerAdvice} when that authorisation arrives. Do not add it here.
 */
@Component
class AuthorizationDeniedListener {

    private static final String EVENT = "auth.authorization.denied";
    private static final String ROLE_PREFIX = "ROLE_";

    @EventListener
    void onAuthorizationDenied(AuthorizationDeniedEvent<?> event) {
        Authentication authentication = event.getAuthentication().get();
        if (!isIdentified(authentication)) {
            return;
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("accountId", accountIdOf(authentication));
        fields.put("role", rolesOf(authentication));
        fields.putAll(currentRequest());
        fields.put("reason", "ROLE");

        BusinessEvents.emit(Level.WARN, EVENT, fields);
    }

    private static boolean isIdentified(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated() && authentication instanceof JwtAuthenticationToken;
    }

    /** The {@code sub} claim as it arrived. Never the email: that is PII and not the key of anything. */
    private static String accountIdOf(Authentication authentication) {
        return authentication.getName();
    }

    /** What the caller <em>does</em> hold, which is the useful half of "why was this denied". */
    private static String rolesOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .sorted()
                .collect(Collectors.joining(","));
    }

    /**
     * Method and path come from the request rather than from the event's object, which is a
     * {@code MethodInvocation} for a method rule and a request for a route rule. The audit wants the
     * same two fields either way.
     */
    private static Map<String, Object> currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Map.of();
        }

        return Map.of(
                "method", attributes.getRequest().getMethod(),
                "path", attributes.getRequest().getRequestURI());
    }
}
