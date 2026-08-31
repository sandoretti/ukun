package dev.sandoretti.ukun.api.shared.observability;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Who to name in a business event: the account behind the current request, as the {@code sub} of its
 * validated token.
 *
 * <p><strong>It answers "who did this", never "may they".</strong> That distinction is the whole
 * reason this sits in {@code shared} next to {@link BusinessEvents} rather than being one more method
 * on {@code IdentityApi}: authorisation is a decision, and {@code docs/08-identidad-y-seguridad.md} §4
 * puts every one of them behind the {@code identity} module so that ownership is always resolved
 * server-side from {@code account_id}. Naming an actor in a log line is not a decision — nothing
 * branches on it — and a module that only audits should not have to declare a dependency on identity
 * to say who it was serving. {@code catalog} is exactly that case: the admin CRUD of RF-10 is
 * authorised by role, with {@code @PreAuthorize} deciding before the method runs, and what the events
 * of {@code docs/11-observabilidad-y-logging.md} §3 still need is the {@code adminId} to attribute
 * them to.
 *
 * <p>It reads the {@code SecurityContextHolder} the same way {@link AccountIdMdcFilter} does — that
 * filter is now its first caller, so the reading exists once instead of twice.
 *
 * <p><strong>{@code adminId} is an account identifier, not an employee one.</strong> The
 * {@code employee} row lives in the {@code staff} module and resolving it would be a query, a module
 * dependency, and a second answer to "who is this" for the sake of a log field. The account UUID is
 * the value that already joins the log line, the MDC and {@code audit_log.actor_account_id}
 * (ADR-0014), which is the correlation the events are for.
 */
public final class CurrentActor {

    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * Most specific first. The three of RF-12, in the order that decides which one names an actor
     * holding more than one.
     */
    private static final List<String> BY_PRECEDENCE = List.of("ADMIN", "EMPLOYEE", "CUSTOMER");

    private CurrentActor() {}

    /**
     * The account behind this request, or {@code null} on anonymous traffic — most of the catalogue,
     * and not an error condition.
     *
     * <p>Text, not a {@link UUID}: this class logs, and parsing is a way of rejecting a token, which is
     * the resource server's job and not this one's ({@link AccountIdMdcFilter} makes the same call for
     * the same reason).
     */
    public static String accountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        return authentication instanceof JwtAuthenticationToken token
                ? token.getToken().getSubject()
                : null;
    }

    /**
     * The role this account acts under, or {@code null} on anonymous traffic.
     *
     * <p><strong>The highest one held, not the one the operation required.</strong> An administrator
     * carries {@code EMPLOYEE} and {@code ADMIN} at once — ADR-0006 makes {@code ADMIN} a sub-flag that
     * only ever travels alongside {@code EMPLOYEE} — so "the role" needs a rule. This one answers
     * <em>who the actor is</em>, which is what {@code audit_log.actor_role} is for; the capacity the
     * operation demanded is already implied by its {@code action}, so recording that instead would be
     * the same fact written twice.
     *
     * <p>Reads the {@code ROLE_} authorities the resource server minted from the token's {@code roles}
     * claim, the same way {@code identity.internal.AuthorizationDeniedListener} does. Text again, and
     * not the {@code identity.Role} enum: this class lives in {@code shared}, which owns no domain
     * vocabulary and must not depend on a module that everything else depends on.
     */
    public static String role() {
        Set<String> held = rolesOf(SecurityContextHolder.getContext().getAuthentication());

        return BY_PRECEDENCE.stream().filter(held::contains).findFirst().orElse(null);
    }

    /**
     * The business roles an authentication carries, with Spring Security's {@code ROLE_} prefix taken
     * off — the three of RF-12, as {@code ukun-auth} minted them into the token's {@code roles} claim.
     *
     * <p>Public and taking its {@link Authentication} as an argument, rather than reading the context
     * like the two methods above, because its second caller does not have one to read:
     * {@code identity.internal.AuthorizationDeniedListener} is handed the authentication of the request
     * that was denied, by the event Spring Security publishes. It shapes the answer differently —
     * every role it holds, for "why was this denied" — and that difference is the reason this returns
     * the set and not a formatted string.
     *
     * <p>The prefix is a Spring Security convention, which is exactly why it belongs in one place: if
     * it ever moves, both readings move with it, and neither is left silently matching nothing.
     */
    public static Set<String> rolesOf(Authentication authentication) {
        if (authentication == null) {
            return Set.of();
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .collect(Collectors.toSet());
    }
}
