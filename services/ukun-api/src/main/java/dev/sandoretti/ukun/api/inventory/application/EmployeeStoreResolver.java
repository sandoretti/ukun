package dev.sandoretti.ukun.api.inventory.application;

import dev.sandoretti.ukun.api.identity.AccessDenied;
import dev.sandoretti.ukun.api.identity.CurrentAccount;
import dev.sandoretti.ukun.api.identity.IdentityApi;
import dev.sandoretti.ukun.api.inventory.infra.EmployeeStoreRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Turns "the request says store 3" into "this employee works in store 3, so let them" — the rule of
 * {@code docs/08-identidad-y-seguridad.md} §4 written once, where the four stock use cases share it.
 *
 * <p><strong>There is no overload that takes a store identifier as proof of anything.</strong> The
 * only thing {@link #requireEmployeeOf(Long)} does with its argument is compare it against what the
 * database says; the store it returns is always the resolved one. That is the shape {@code CustomersApi}
 * gives the same rule for orders, and it is what makes the legacy antipattern — trusting a
 * {@code tiendaId} that arrived in the request — impossible to write here by accident rather than
 * merely discouraged.
 *
 * <p>The check runs on every request instead of caching the mapping, and the note in {@code docs/08}
 * §4 says why that is affordable: one look-up on a unique index, cacheable with a short TTL the day it
 * shows up in a profile. An employee moved to another store is denied their old one on the very next
 * request, which is the property a cached or token-borne store would give away.
 */
@Component
class EmployeeStoreResolver {

    private final IdentityApi identity;
    private final EmployeeStoreRepository employees;

    EmployeeStoreResolver(IdentityApi identity, EmployeeStoreRepository employees) {
        this.identity = identity;
        this.employees = employees;
    }

    /**
     * The employee behind the token, provided the store in the path is the one they work in.
     *
     * @throws AccessDeniedException if the account has no employee row, or works somewhere else. Both
     *     answer {@code 403} with the same body: telling the caller <em>which</em> of the two it was
     *     would confirm that a store exists to somebody who has no business knowing.
     */
    CurrentEmployee requireEmployeeOf(Long storeId) {
        CurrentAccount account = identity.requireCurrentAccount();

        CurrentEmployee employee = employees
                .findByAccountId(account.accountId())
                .map(row -> new CurrentEmployee(row.getEmployeeId(), row.getStoreId()))
                .orElseThrow(() -> AccessDenied.foreignResource(account, "no employee profile"));

        if (!employee.storeId().equals(storeId)) {
            throw AccessDenied.foreignResource(account, "store " + storeId + " is not theirs");
        }
        return employee;
    }
}
