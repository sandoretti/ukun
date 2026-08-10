package dev.sandoretti.ukun.api.identity;

import java.util.Set;

/**
 * The account to reserve in ukun-auth: credentials and roles, nothing else. The body of
 * {@code POST /internal/accounts} in {@code docs/api/openapi/ukun-auth.yaml}.
 *
 * <p><strong>There is no name, no last name and no profile data here</strong>, and that is ADR-0004
 * rather than an oversight: those live in this service's own database, so editing a profile stays one
 * local transaction instead of a write across two databases.
 *
 * <p>{@code enabled} is {@code false} for the reservation step of the saga ({@code docs/08} §3): a
 * disabled account allows no login, so an abandoned reservation is inert until reconciliation deletes
 * it.
 */
public record CreateAccountCommand(String email, String password, Set<Role> roles, boolean enabled) {

    public CreateAccountCommand {
        roles = Set.copyOf(roles);
    }

    /**
     * Hand-written on purpose. A record prints every component unless told otherwise, so the
     * compiler's version of this method would put a plaintext password into any log line, exception
     * message or debugger frame that touched it — which is defect #10 reappearing through a different
     * door ({@code docs/11-observabilidad-y-logging.md} §4).
     *
     * <p>{@code HardRules.DTOS_CARRYING_A_SECRET_MASK_IT} fails the build if this is deleted; the
     * rule is the safety net, this method is the fix.
     */
    @Override
    public String toString() {
        return "CreateAccountCommand[email=%s, password=***, roles=%s, enabled=%s]"
                .formatted(email, roles, enabled);
    }
}
