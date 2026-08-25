package dev.sandoretti.ukun.api.staff.application;

import dev.sandoretti.ukun.api.stores.StoreSummary;
import dev.sandoretti.ukun.api.stores.StoresApi;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/me/employment}: which store the employee holding this token works in (RF-09).
 *
 * <p>Two reads and no branch. The first is the one that decides everything —
 * {@link CurrentEmployeeResolver} turns the {@code sub} of the token into the {@code employee} row, or
 * denies the request — and the second only puts a name on the store it returned.
 *
 * <p><strong>Why it lives in {@code staff} and not in {@code customers} next to the rest of
 * {@code /api/me}</strong>: this module owns {@code employee} ({@code docs/14-modules.md} §1), and the
 * module that owns the data answers the question. Serving it from {@code customers} would mean that
 * module reading employee rows, which is an edge {@code ApplicationModules.verify()} rejects — the
 * same reason {@code inventory} owns {@code /api/availability} (ADR-0017 §3).
 *
 * <p>{@code @Transactional(readOnly = true)} and nothing in the constructor but assignments: rule 6 of
 * the skill {@code spring-api} keeps look-ups out of bean construction, which is defect #6 of the
 * legacy — two services that resolve a store while the context is starting and refuse to start at all
 * when the row is missing.
 */
@Service
@Transactional(readOnly = true)
public class MyEmploymentService {

    private final CurrentEmployeeResolver employees;
    private final StoresApi stores;

    MyEmploymentService(CurrentEmployeeResolver employees, StoresApi stores) {
        this.employees = employees;
        this.stores = stores;
    }

    /**
     * The employment of whoever is making this request.
     *
     * <p>The response is built with the constructor rather than through {@link EmployeeMapper}, the
     * way {@code CurrentUserService} builds the body of {@code GET /api/me}: what ADR-0009 replaced
     * with MapStruct is the hand-written factory that copies an entity field by field, and there is no
     * entity here — three values from two different modules, none of which a generated mapper would
     * know how to fetch.
     */
    public EmploymentResponse current() {
        CurrentEmployee employee = employees.requireCurrentEmployee();

        return new EmploymentResponse(employee.employeeId(), employee.storeId(), nameOf(employee.storeId()));
    }

    /**
     * {@link IllegalStateException} and not a {@code 404}: {@code fk_employee_store} guarantees the row
     * exists, so its absence means this service lost a store between two statements of the same
     * request. That is a {@code 500} with a stack trace, which is what gets it fixed; a {@code 404}
     * would tell an employee that their own workplace does not exist.
     */
    private String nameOf(Long storeId) {
        StoreSummary store = stores.summariesByIds(Set.of(storeId)).get(storeId);
        if (store == null) {
            throw new IllegalStateException("Employee works in store %d, which does not exist".formatted(storeId));
        }

        return store.name();
    }
}
