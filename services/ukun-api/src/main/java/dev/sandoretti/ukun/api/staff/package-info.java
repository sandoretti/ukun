/**
 * Employees: creation and listing per store (RF-11), under the admin surface of ADR-0020.
 * Owns: {@code employee}.
 *
 * <p>Depends on {@code stores} for the employee's store and on {@code identity} for their account
 * (ADR-0017 §1).
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"stores", "identity"}
)
package dev.sandoretti.ukun.api.staff;
