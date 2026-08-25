package dev.sandoretti.ukun.api.staff.web;

import dev.sandoretti.ukun.api.shared.openapi.ApiSecurityScheme;
import dev.sandoretti.ukun.api.shared.openapi.BadRequestResponse;
import dev.sandoretti.ukun.api.shared.openapi.ConflictResponse;
import dev.sandoretti.ukun.api.shared.openapi.ForbiddenResponse;
import dev.sandoretti.ukun.api.shared.openapi.InternalErrorResponse;
import dev.sandoretti.ukun.api.shared.openapi.UnauthenticatedResponse;
import dev.sandoretti.ukun.api.shared.web.PageResponse;
import dev.sandoretti.ukun.api.shared.web.PaginationParams;
import dev.sandoretti.ukun.api.staff.application.AdminEmployeeService;
import dev.sandoretti.ukun.api.staff.application.CreateEmployeeRequest;
import dev.sandoretti.ukun.api.staff.application.EmployeeCreation;
import dev.sandoretti.ukun.api.staff.application.EmployeeFilters;
import dev.sandoretti.ukun.api.staff.application.EmployeeResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The management of employees (RF-11). {@code ADMIN} only, and under {@code /api/admin/**} because of
 * it — ADR-0020 makes the prefix a biconditional: nothing admin-only outside it, nothing but admin-only
 * inside.
 *
 * <p><strong>The prefix authorises nothing.</strong> The gateway's {@code /api/admin/** → ROLE_ADMIN}
 * rule is defence in depth and this service never takes it on trust: the {@code @PreAuthorize} below is
 * the authority, decided against a token this service validated itself
 * ({@code docs/08-identidad-y-seguridad.md} §4).
 *
 * <p>Neither operation names a store, and that is the design rather than an omission — see
 * {@code AdminEmployeeService}. It is also why there is no {@code GET /api/admin/employees/{id}} for
 * the {@code Location} of the alta to point at: the contract declares the header, and the screen that
 * reads it is the listing.
 *
 * <p>Editing and deleting are out of scope for v1.0 (RF-11), so this class has two methods and will
 * keep having two until an ADR says otherwise.
 */
@RestController
@RequestMapping(path = "/api/admin/employees", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = ApiSecurityScheme.BEARER_AUTH)
class AdminEmployeeController {

    private final AdminEmployeeService employees;

    AdminEmployeeController(AdminEmployeeService employees) {
        this.employees = employees;
    }

    /**
     * {@code @ParameterObject} on both bound objects, for the reason the other listings document:
     * without it springdoc publishes two opaque parameters called {@code filters} and
     * {@code pagination}, and {@code OpenApiDriftIT} stops matching the frozen contract while the
     * endpoint keeps working.
     */
    @GetMapping
    @ApiResponse(responseCode = "200", description = "A page of employees of the admin's store.")
    @BadRequestResponse
    @UnauthenticatedResponse
    @ForbiddenResponse
    @InternalErrorResponse
    PageResponse<EmployeeResponse> listEmployees(
            @Valid @ParameterObject EmployeeFilters filters, @Valid @ParameterObject PaginationParams pagination) {

        return employees.list(filters, pagination);
    }

    /**
     * The alta of RF-11, which is the signup saga with the roles of an employee.
     *
     * <p>Two successful statuses, and the difference between them is real: {@code 201} when the account
     * is active and the person can log in, {@code 202} when the employee exists but the activation has
     * not been confirmed and repeating the alta finishes it. The {@code 202} carries no body because the
     * contract gives it none — the employee is in the listing either way, which is where the admin
     * looks next.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponse(
            responseCode = "201",
            description = "Employee created and account activated.",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = EmployeeResponse.class)))
    @ApiResponse(
            responseCode = "202",
            description = "Employee created, account activation pending confirmation.",
            content = @Content)
    @BadRequestResponse
    @UnauthenticatedResponse
    @ForbiddenResponse
    @ConflictResponse
    @InternalErrorResponse
    ResponseEntity<EmployeeResponse> createEmployee(@Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeCreation created = employees.create(request);

        return switch (created.outcome()) {
            case COMPLETED -> ResponseEntity.created(locationOf(created.employee()))
                    .body(created.employee());
            case ACTIVATION_PENDING -> ResponseEntity.accepted().build();
        };
    }

    private static URI locationOf(EmployeeResponse employee) {
        return URI.create("/api/admin/employees/" + employee.id());
    }
}
