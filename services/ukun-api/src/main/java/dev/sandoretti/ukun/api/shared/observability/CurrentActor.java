package dev.sandoretti.ukun.api.shared.observability;

import java.util.UUID;
import org.springframework.security.core.Authentication;
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
}
