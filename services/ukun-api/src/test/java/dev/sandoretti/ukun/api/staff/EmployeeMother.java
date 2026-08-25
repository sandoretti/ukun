package dev.sandoretti.ukun.api.staff;

import dev.sandoretti.ukun.api.staff.domain.Employee;
import java.util.UUID;

/**
 * The staff of a store, built explicitly so each test says what it is about (skill {@code testing}).
 *
 * <p>Three of these people carry a detail the listing is asserted against, and none of it is
 * decoration:
 *
 * <ul>
 *   <li>{@link #angela} is spelled with two accents nobody types into a search box — defect #22.
 *   <li>{@link #luis} is built twice with the same name and the same last name, which is the real tie
 *       the {@code id} tie-breaker of defect #23 exists for. Sorted without it, one of the two can come
 *       back on two pages or on none.
 *   <li>{@link #carmen} is the only admin of her store, so "filter by {@code isAdmin}" has exactly one
 *       right answer instead of a plausible-looking one.
 * </ul>
 */
public final class EmployeeMother {

    private EmployeeMother() {}

    /** The admin whose token every request that is meant to succeed is made with. */
    public static Employee carmen(UUID accountId, long storeId) {
        return employee(accountId, "Carmen", "Ruiz", "carmen@ukun.com", "Encargada", true, storeId);
    }

    /** An ordinary employee of the same store: he shows up in the listing and his token does not get in. */
    public static Employee diego(UUID accountId, long storeId) {
        return employee(accountId, "Diego", "Serrano", "diego@ukun.com", "Dependiente", false, storeId);
    }

    /** Accented name and surname: searching {@code "angela munoz"} has to find her. */
    public static Employee angela(UUID accountId, long storeId) {
        return employee(accountId, "Ángela", "Muñoz", "angela@ukun.com", "Dependienta", false, storeId);
    }

    /** Two of these are seeded, deliberately indistinguishable except by their id. */
    public static Employee luis(UUID accountId, long storeId) {
        return employee(accountId, "Luis", "García", "luis@ukun.com", "Mozo de almacén", false, storeId);
    }

    /** The admin of the other store. Nothing of hers may appear in anybody else's listing. */
    public static Employee elena(UUID accountId, long storeId) {
        return employee(accountId, "Elena", "Soto", "elena@ukun.com", "Encargada", true, storeId);
    }

    private static Employee employee(
            UUID accountId,
            String name,
            String lastName,
            String email,
            String jobTitle,
            boolean admin,
            long storeId) {

        Employee employee = new Employee();
        employee.setAccountId(accountId);
        employee.setName(name);
        employee.setLastName(lastName);
        employee.setEmail(email);
        employee.setJobTitle(jobTitle);
        employee.setAdmin(admin);
        employee.setStoreId(storeId);

        return employee;
    }
}
