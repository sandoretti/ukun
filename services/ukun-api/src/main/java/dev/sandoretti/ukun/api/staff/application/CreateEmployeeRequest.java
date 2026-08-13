package dev.sandoretti.ukun.api.staff.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /api/admin/employees} (RF-11).
 *
 * <p><strong>There is no {@code storeId} here, and adding one would be the defect.</strong> A new
 * employee's store is always the store of the admin creating them, resolved server-side from their
 * {@code account_id} ({@code docs/08-identidad-y-seguridad.md} §3 and §4, RF-11). Accepting it in the
 * body would turn an authorisation fact into a client-controlled parameter — which is the legacy's
 * habit of trusting a {@code tiendaId} that arrived in the request, in a new place.
 *
 * <p>Nor is there a {@code roles} array: the account's roles are derived from {@code isAdmin}, because
 * {@code EMPLOYEE} is implied and {@code ADMIN} is the only real choice inside an employee (RF-12).
 */
public record CreateEmployeeRequest(
        @NotBlank @Email @Size(max = 255) String email,
        /*
         * @NotBlank only, no @Size — the same call SignupRequest documents: the length rules live in
         * ukun-auth's PasswordPolicy, the service that owns credentials. A second declaration here
         * would be a rule with two owners, and the copy that drifts is always the one further from the
         * decision.
         */
        @NotBlank String password,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(max = 50) String jobTitle,
        @NotNull @Schema(defaultValue = "false") Boolean isAdmin) {

    /**
     * Hand-written, and the reason is defect #10 rather than style: a record prints every component, so
     * the generated version would put a plaintext password into any log line or exception message that
     * touched this object — and the validation advice touches it on every rejected form.
     *
     * <p>{@code HardRules.DTOS_CARRYING_A_SECRET_MASK_IT} fails the build if this is deleted.
     */
    @Override
    public String toString() {
        return "CreateEmployeeRequest[email=%s, password=***, name=%s, lastName=%s, jobTitle=%s, isAdmin=%s]"
                .formatted(email, name, lastName, jobTitle, isAdmin);
    }
}
