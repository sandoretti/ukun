package dev.sandoretti.ukun.api.identity;

import dev.sandoretti.ukun.api.shared.observability.AuthorizationDenied;
import dev.sandoretti.ukun.api.shared.observability.AuthorizationDenied.Reason;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;

/**
 * The {@code FOREIGN_RESOURCE} denial: the {@code 403} a use case raises when the caller is who they
 * say they are and the resource is still not theirs, audited as it is raised.
 *
 * <p><strong>It is emitted where the decision is taken and not in the advice</strong>, unlike what an
 * earlier comment in {@code GlobalExceptionHandler} anticipated. Spring Security publishes no event
 * for this denial because it is a domain decision, and the domain is the only layer that holds both
 * halves of it: which store was asked for and which one the employee actually works in. The advice
 * sees an {@code AccessDeniedException} and cannot tell it apart from a role rule that already
 * reported itself — auditing there would either double the role denials or lose the reason of this
 * one.
 *
 * <p>The role half stays in {@code identity}'s listener, from the event Spring Security does publish.
 * Two publishers, one shape, which is what {@link AuthorizationDenied} in {@code shared} is for.
 *
 * <p><strong>Why here and not in {@code shared} next to that class.</strong> This one reads a
 * {@link CurrentAccount}, and {@code shared} is the module everybody else depends on — it takes the
 * plain values a caller already holds precisely so that no module's model leaks into it. The account
 * is this module's model, so the half that knows about it lives here, where both callers already have
 * a declared dependency (ADR-0017 §1). It stopped being one caller's private method with issue #45:
 * {@code inventory} resolves the store of a stock line and {@code staff} the store of an admin, and
 * the two had the same ten lines with the same reasoning written twice.
 */
public final class AccessDenied {

    private AccessDenied() {}

    /**
     * Audits the denial and builds the exception the advice turns into a {@code 403}.
     *
     * @param detail what went wrong, for the log. It does not reach the client: the response body is
     *     the same for every denial, because telling a caller <em>which</em> check turned them away
     *     confirms the existence of a resource they have no business knowing about
     */
    public static AccessDeniedException foreignResource(CurrentAccount account, String detail) {
        AuthorizationDenied.emit(account.accountId(), rolesOf(account), Reason.FOREIGN_RESOURCE);

        return new AccessDeniedException("Access denied: " + detail);
    }

    /** What the caller <em>does</em> hold, which is the useful half of "why was this denied". */
    private static String rolesOf(CurrentAccount account) {
        return account.roles().stream().map(Role::name).sorted().collect(Collectors.joining(","));
    }
}
