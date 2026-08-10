package dev.sandoretti.ukun.api.identity;

import java.util.Set;
import java.util.UUID;

/**
 * Who is making the current request, as this service reads it from the validated token. It is the
 * whole of what the JWT says about a person: there is nothing else in it, by design.
 *
 * <p><strong>{@code accountId} is the identifier everything else hangs from.</strong> It is the
 * {@code sub} claim, which ukun-auth sets to the UUID of {@code account} rather than to the email the
 * person typed — an email can change, and if identity were keyed by it, changing it would silently
 * change the owner of every resource. Resource ownership is resolved from this value against
 * {@code customer.account_id} / {@code employee.account_id} through their unique index
 * ({@code docs/08-identidad-y-seguridad.md} §1 and §4).
 *
 * <p>{@code email} is a projection for display, kept in step by whoever needs it (§2 of that
 * document). It is never the key of anything here.
 *
 * <p><strong>What this record deliberately does not have is {@code storeId}</strong>, and it is not
 * an omission ({@code docs/08} §4): baking a store into a credential would teach ukun-auth what a
 * store is, and would leave an employee's transfer without effect until their token expired. The
 * store is resolved server-side from {@code accountId}, one indexed query.
 */
public record CurrentAccount(UUID accountId, String email, Set<Role> roles) {

    public CurrentAccount {
        roles = Set.copyOf(roles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
