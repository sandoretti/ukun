package dev.sandoretti.ukun.api.inventory.infra;

import dev.sandoretti.ukun.api.inventory.domain.StoreStock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Which store the employee behind the current token works in, and their own identifier — the two
 * values every stock operation needs before it can decide anything ({@code docs/08-identidad-y-seguridad.md}
 * §4: the {@code {storeId}} of the path identifies the resource and authorises nothing).
 *
 * <p><strong>This reads a table that belongs to another module, and that is a deliberate, temporary
 * exception.</strong> {@code employee} is owned by {@code staff} ({@code docs/14-modules.md} §1),
 * which was an empty package when this was written. Issue #45 has since filled it, but the swap did
 * not come with it: reading through a {@code StaffApi} means declaring {@code inventory → staff}, and
 * that edge is not in the declared graph of ADR-0017 §1 — which {@code ModulithTest} asserts — so it
 * is an ADR, not a slice's decision.
 *
 * <p>So the whole of the exception is <em>this file</em>: one native read of two columns, no entity
 * mapped, nothing written. The day that edge is declared, this class is deleted and
 * {@code EmployeeStoreResolver} calls {@code StaffApi} instead — a one-line change at a single call
 * site, which is the reason the borrowing was confined here rather than spread over the use cases.
 *
 * <p>It extends {@code JpaRepository<StoreStock, Long>} only because Spring Data needs a managed
 * entity to attach a repository to, and {@code StoreStock} is the one this module owns. No method of
 * the inherited interface is used or meant to be: the single query below is native and names its own
 * table.
 */
public interface EmployeeStoreRepository extends JpaRepository<StoreStock, Long> {

    /**
     * The employee row behind an account, or empty when the token belongs to somebody who is not one.
     *
     * <p>Empty is a legitimate answer, not a broken invariant: a customer's token reaching a stock
     * endpoint is exactly the {@code 403} of {@code docs/08} §4, and so is an employee whose row was
     * removed. The caller decides that; this only reports what the table says.
     *
     * <p>Backed by {@code ux_employee_account_id}, so it is a single indexed look-up per request. The
     * projection is closed and returns identifiers only — nothing that could tempt a caller into
     * treating this as a way to read employee data.
     */
    @Query(
            value =
                    """
                    SELECT e.id AS employeeId, e.store_id AS storeId
                      FROM employee e
                     WHERE e.account_id = :accountId
                    """,
            nativeQuery = true)
    Optional<EmployeeStore> findByAccountId(@Param("accountId") UUID accountId);

    /** The employee's own identifier and the store they work in. */
    interface EmployeeStore {

        Long getEmployeeId();

        Long getStoreId();
    }
}
