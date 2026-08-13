package dev.sandoretti.ukun.api.staff.application;

import dev.sandoretti.ukun.api.shared.observability.BusinessEvents;
import dev.sandoretti.ukun.api.shared.observability.CurrentActor;
import dev.sandoretti.ukun.api.staff.domain.Employee;
import dev.sandoretti.ukun.api.staff.infra.EmployeeRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The local step of the alta of an employee: one row, in one transaction, behind an account
 * {@code ukun-auth} has already reserved.
 *
 * <p>A bean of its own rather than a method of {@link AdminEmployeeService} for the reason the saga
 * makes concrete — the orchestrator runs outside any transaction because it makes two HTTP calls, and
 * a private method calling itself would silently get no transaction at all. It is the employee's
 * counterpart of {@code CustomerRegistrar}, and it is deliberately the simpler of the two: an employee
 * owns no address and no card, so the single transaction here writes a single row.
 *
 * <p>The {@code employee.created} event is emitted <strong>inside</strong> this transaction, which is
 * where {@code docs/11-observabilidad-y-logging.md} §2 puts the events that generate an audit trail —
 * and where the {@code audit_log} row of issue #47 will be written without moving anything.
 */
@Component
class EmployeeRegistrar {

    private static final String CREATED_EVENT = "employee.created";

    private final EmployeeRepository employees;
    private final EmployeeMapper mapper;

    EmployeeRegistrar(EmployeeRepository employees, EmployeeMapper mapper) {
        this.employees = employees;
        this.mapper = mapper;
    }

    /**
     * @param accountId the account ukun-auth reserved, stored opaquely and without a foreign key
     *     because it lives in another database ({@code docs/08-identidad-y-seguridad.md} §1)
     * @param storeId the store of the admin running the request, resolved server-side and never taken
     *     from the request body
     * @return the id of the new employee, which the saga hands back to the caller
     */
    @Transactional
    long register(UUID accountId, CreateEmployeeRequest request, Long storeId) {
        // employee.email is the read projection of docs/08 §2, not the credential — that one stays in
        // ukun-auth, and this copy is refreshed from the token's claim on its owner's next request.
        Employee employee = employees.save(mapper.toEmployee(request, accountId, storeId));

        BusinessEvents.emit(
                Level.INFO,
                CREATED_EVENT,
                Map.of(
                        "employeeId", employee.getId(),
                        "accountId", accountId,
                        "storeId", storeId,
                        "isAdmin", employee.isAdmin(),
                        // The sub of the token and not a row id: docs/11 §3 says why — resolving the
                        // admin's own employee id would be a query to fill in a log field, and this is
                        // the value that already joins the log line, the MDC and audit_log.
                        "adminId", CurrentActor.accountId()));

        return employee.getId();
    }
}
