package dev.sandoretti.ukun.api.staff.application;

import dev.sandoretti.ukun.api.identity.AccessDenied;
import dev.sandoretti.ukun.api.identity.CurrentAccount;
import dev.sandoretti.ukun.api.identity.IdentityApi;
import dev.sandoretti.ukun.api.staff.domain.Employee;
import dev.sandoretti.ukun.api.staff.infra.EmployeeRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Which store the admin running this request administers — the one question both operations of RF-11
 * have to answer before they can do anything, because the store is never in the request.
 *
 * <p>The role is already decided by the {@code @PreAuthorize} of the controller; what is left is the
 * half a token cannot answer: <em>which</em> store. That comes from the {@code employee} row behind the
 * account's {@code sub}, never from a path variable or a body field
 * ({@code docs/08-identidad-y-seguridad.md} §4). It is the same shape {@code EmployeeStoreResolver}
 * gives the rule in {@code inventory}, minus the comparison — here there is nothing to compare against,
 * which is the point: an admin cannot even name another store.
 *
 * <p>The look-up runs on every request rather than being cached, for the reason {@code docs/08} §4
 * gives: it is one hit on a unique index, and an admin moved to another store is answered with the new
 * one on their very next request, which a cached or token-borne store would not do.
 */
@Component
class AdminStoreResolver {

    private final IdentityApi identity;
    private final EmployeeRepository employees;

    AdminStoreResolver(IdentityApi identity, EmployeeRepository employees) {
        this.identity = identity;
        this.employees = employees;
    }

    /**
     * @return the store of the admin behind the token
     * @throws AccessDeniedException if the account has no employee row at all. A token can carry
     *     {@code ADMIN} and still belong to nobody in this database — an employee whose row was
     *     removed, or a role granted out of band — and an administrator of no store administers
     *     nothing
     */
    Long requireStoreOfCurrentAdmin() {
        CurrentAccount account = identity.requireCurrentAccount();

        return employees
                .findByAccountId(account.accountId())
                .map(Employee::getStoreId)
                .orElseThrow(() -> AccessDenied.foreignResource(account, "no employee profile"));
    }
}
