package dev.sandoretti.ukun.api.inventory.application;

import dev.sandoretti.ukun.api.identity.CurrentAccount;
import dev.sandoretti.ukun.api.identity.IdentityApi;
import dev.sandoretti.ukun.api.identity.Role;
import dev.sandoretti.ukun.api.inventory.infra.EmployeeStoreRepository;
import dev.sandoretti.ukun.api.shared.observability.BusinessEvents;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.event.Level;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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

    private static final String DENIED_EVENT = "auth.authorization.denied";

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
                .orElseThrow(() -> denied(account, "no employee profile"));

        if (!employee.storeId().equals(storeId)) {
            throw denied(account, "store " + storeId + " is not theirs");
        }
        return employee;
    }

    /**
     * Emits the {@code FOREIGN_RESOURCE} half of {@code auth.authorization.denied}
     * ({@code docs/11-observabilidad-y-logging.md} §3) and builds the exception the advice turns into a
     * {@code 403}.
     *
     * <p><strong>It is emitted here and not in the advice</strong>, unlike what an earlier comment in
     * {@code GlobalExceptionHandler} anticipated. Spring Security publishes no event for this denial
     * because it is a domain decision, and the domain is the only layer that holds both halves of it:
     * which store was asked for and which one the employee actually works in. The advice sees an
     * {@code AccessDeniedException} and cannot tell it apart from a role rule that already reported
     * itself — auditing there would either double the role denials or lose the reason of this one.
     *
     * <p>The role half stays where it is, in {@code identity}'s listener. Same event name, same fields,
     * two publishers, exactly as {@code docs/11} §3 describes it.
     */
    private AccessDeniedException denied(CurrentAccount account, String detail) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("accountId", account.accountId());
        fields.put("role", rolesOf(account));
        fields.putAll(currentRequest());
        fields.put("reason", "FOREIGN_RESOURCE");

        BusinessEvents.emit(Level.WARN, DENIED_EVENT, fields);

        return new AccessDeniedException("Access denied: " + detail);
    }

    private static String rolesOf(CurrentAccount account) {
        return account.roles().stream().map(Role::name).sorted().collect(Collectors.joining(","));
    }

    /** Method and path, read the same way {@code identity}'s listener reads them for the role half. */
    private static Map<String, Object> currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return Map.of();
        }

        return Map.of(
                "method", attributes.getRequest().getMethod(),
                "path", attributes.getRequest().getRequestURI());
    }
}
