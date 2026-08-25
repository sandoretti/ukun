package dev.sandoretti.ukun.api.staff.infra;

import dev.sandoretti.ukun.api.staff.domain.Employee;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * The {@code employee} table, which this module owns.
 *
 * <p>Two reads and one write, and no derived query beyond {@link #findByAccountId}: the listing's
 * filters are optional and combine, so they are a composed {@link Specification} rather than the
 * explosion of {@code findByStoreIdAndAdminAndNameContaining…} methods that
 * {@code docs/03-analisis-y-patrones.md} §2 rules out.
 */
public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    /**
     * The employee behind an account, or empty when the token belongs to somebody who is not one.
     *
     * <p>Empty is a legitimate answer and not a broken invariant: a customer's token reaching this
     * module, or an admin whose employee row was removed, is the {@code 403} of {@code docs/08} §4.
     * Backed by {@code ux_employee_account_id}, so it is one indexed look-up per request.
     */
    Optional<Employee> findByAccountId(UUID accountId);
}
