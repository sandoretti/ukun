package dev.sandoretti.ukun.api.identity.internal;

import dev.sandoretti.ukun.api.shared.observability.AuthorizationDenied;
import dev.sandoretti.ukun.api.shared.observability.AuthorizationDenied.Reason;
import dev.sandoretti.ukun.api.shared.observability.CurrentActor;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

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
 * {@code AuthorizationDeniedEvent} to hang from — it is a domain decision — and is emitted by the use
 * case that takes it, through {@code AccessDenied} in this same module. Same event name and same
 * fields, two publishers. Do not add it here: this class cannot see which store was asked for, so the
 * reason it wrote would be a guess.
 */
@Component
class AuthorizationDeniedListener {

    @EventListener
    void onAuthorizationDenied(AuthorizationDeniedEvent<?> event) {
        Authentication authentication = event.getAuthentication().get();
        if (!isIdentified(authentication)) {
            return;
        }

        AuthorizationDenied.emit(accountIdOf(authentication), rolesOf(authentication), Reason.ROLE);
    }

    private static boolean isIdentified(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated() && authentication instanceof JwtAuthenticationToken;
    }

    /**
     * The {@code sub} claim as it arrived. Never the email: that is PII and not the key of anything
     * ({@code docs/11-observabilidad-y-logging.md} §5).
     *
     * <p>Read from the token rather than through {@code getName()}, which returns whatever claim
     * {@code spring.security.oauth2.resourceserver.jwt.principal-claim-name} names — a property that
     * lives in the same block of {@code application.yaml} as {@code authorities-claim-name}, so it is
     * one line away from turning this field into the caller's address on every 403. What this event
     * publishes is a decision of this class, not of a property nobody has declared. Same read as
     * {@code shared/observability/AccountIdMdcFilter}.
     */
    private static String accountIdOf(Authentication authentication) {
        return ((JwtAuthenticationToken) authentication).getToken().getSubject();
    }

    /**
     * What the caller <em>does</em> hold, which is the useful half of "why was this denied".
     *
     * <p>The reading of the authorities is {@code CurrentActor}'s since v0.7, when the audit trail
     * needed the same one to name an actor. What stays here is the shaping: every role, sorted, in one
     * field — where the trail wants the single highest one.
     */
    private static String rolesOf(Authentication authentication) {
        return CurrentActor.rolesOf(authentication).stream().sorted().collect(Collectors.joining(","));
    }
}
