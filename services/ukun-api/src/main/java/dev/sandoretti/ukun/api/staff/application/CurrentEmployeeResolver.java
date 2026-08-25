package dev.sandoretti.ukun.api.staff.application;

import dev.sandoretti.ukun.api.identity.AccessDenied;
import dev.sandoretti.ukun.api.identity.CurrentAccount;
import dev.sandoretti.ukun.api.identity.IdentityApi;
import dev.sandoretti.ukun.api.staff.infra.EmployeeRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Who the person behind the {@code sub} of this token is, as an employee of this shop — the one
 * question every use case of this module has to answer before it can do anything, because neither the
 * employee nor their store ever travels in the request.
 *
 * <p>The role is already decided by the {@code @PreAuthorize} of the controller; what is left is the
 * half a token cannot answer: <em>which</em> employee, and <em>which</em> store. That comes from the
 * {@code employee} row behind the account's {@code sub}, never from a path variable or a body field
 * ({@code docs/08-identidad-y-seguridad.md} §4). It is the same shape {@code EmployeeStoreResolver}
 * gives the rule in {@code inventory}, minus the comparison — here there is nothing to compare
 * against, which is the point: an employee cannot even name another store.
 *
 * <p>The look-up runs on every request rather than being cached, for the reason {@code docs/08} §4
 * gives: it is one hit on a unique index, and an employee moved to another store is answered with the
 * new one on their very next request, which a cached or token-borne store would not do.
 */
@Component
class CurrentEmployeeResolver {

    private final IdentityApi identity;
    private final EmployeeRepository employees;

    CurrentEmployeeResolver(IdentityApi identity, EmployeeRepository employees) {
        this.identity = identity;
        this.employees = employees;
    }

    /**
     * The employee behind the token.
     *
     * @throws AccessDeniedException if the account has no employee row at all. A token can carry
     *     {@code EMPLOYEE} — or even {@code ADMIN} — and still belong to nobody in this database: an
     *     employee whose row was removed, or a role granted out of band. It is a {@code 403} and never
     *     a {@code 404}: the resource being asked for is the caller's own employment, and answering
     *     "not found" would describe the request as valid and unlucky rather than as denied
     */
    CurrentEmployee requireCurrentEmployee() {
        CurrentAccount account = identity.requireCurrentAccount();

        return employees
                .findByAccountId(account.accountId())
                .map(employee -> new CurrentEmployee(employee.getId(), employee.getStoreId()))
                .orElseThrow(() -> AccessDenied.foreignResource(account, "no employee profile"));
    }

    /**
     * The store of the admin behind the token, for the two operations of RF-11 — which need the store
     * and nothing else about the person.
     *
     * <p>It reads {@code Admin} and resolves an employee, and the mismatch is deliberate: whether the
     * caller is an admin was settled by {@code @PreAuthorize} before the method ran, so what is left to
     * resolve here is identical for both kinds of employee. An admin of no store administers nothing,
     * which is the same denial as above.
     */
    Long requireStoreOfCurrentAdmin() {
        return requireCurrentEmployee().storeId();
    }
}
