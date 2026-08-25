package dev.sandoretti.ukun.api.staff.application;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * An employee as the back-office reads them ({@code Employee} in the contract).
 *
 * <p>No password and no account identifier: the first never leaves ukun-auth, and the second is an
 * internal correlation key with nothing to offer a screen that already has the employee's own id.
 *
 * <p>{@code email} is the read projection of {@code docs/08-identidad-y-seguridad.md} §2 and may be
 * stale until that employee logs in again — the contract says so in the operation's description, and
 * it is an accepted consequence of the identity boundary rather than a bug to chase.
 */
public record EmployeeResponse(
        Long id,
        String name,
        String lastName,
        @Schema(format = "email") String email,
        String jobTitle,
        boolean isAdmin,
        Long storeId) {}
