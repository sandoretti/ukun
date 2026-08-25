package dev.sandoretti.ukun.api.staff.web;

import dev.sandoretti.ukun.api.shared.openapi.ApiSecurityScheme;
import dev.sandoretti.ukun.api.shared.openapi.ForbiddenResponse;
import dev.sandoretti.ukun.api.shared.openapi.InternalErrorResponse;
import dev.sandoretti.ukun.api.shared.openapi.UnauthenticatedResponse;
import dev.sandoretti.ukun.api.staff.application.EmploymentResponse;
import dev.sandoretti.ukun.api.staff.application.MyEmploymentService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/me/employment}: the store of the employee behind this session, which the stock
 * back-office reads before it can build any of the four routes of RF-09 — they all carry
 * {@code {storeId}} in the path.
 *
 * <p><strong>It is not under {@code /api/admin/**}, and that is the rule rather than an exception.</strong>
 * ADR-0020 makes the prefix a biconditional, and this operation is not admin-only: the permission
 * matrix of {@code docs/02-requisitos.md} §3 gives "see and edit the stock of their store" a ✅ for an
 * ordinary employee, which is why RF-09 lives outside the prefix and why this does too.
 * {@code hasRole('EMPLOYEE')} is the whole of its role check — an admin holds that role as well
 * (RF-12), so nothing extra is needed to let them in.
 *
 * <p>It takes no parameter, and there is nothing to add: whose employment is answered is decided by
 * the token and only by it. An identifier in a request identifies a resource, it never authorises
 * access to it ({@code docs/08-identidad-y-seguridad.md} §4), so an endpoint that asked for a
 * {@code storeId} here would be asking a question it must not believe the answer to.
 *
 * <p>A controller of its own rather than a method on the {@code customers} one that serves the rest of
 * {@code /api/me}: the URL space is shared, the modules are not — see {@link MyEmploymentService}.
 */
@RestController
@RequestMapping(path = "/api/me/employment", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('EMPLOYEE')")
@SecurityRequirement(name = ApiSecurityScheme.BEARER_AUTH)
class MyEmploymentController {

    private final MyEmploymentService employment;

    MyEmploymentController(MyEmploymentService employment) {
        this.employment = employment;
    }

    /**
     * The {@code 403} covers both halves of the code: a customer's token, stopped by the
     * {@code @PreAuthorize} above, and an {@code EMPLOYEE} token with no {@code employee} row behind
     * it, stopped by the resolution inside. Both answer the same body on purpose — telling the caller
     * which of the two it was would confirm that the other check passed.
     *
     * <p>The method name is the {@code operationId} springdoc publishes; the SPA's client is named
     * after it.
     */
    @GetMapping
    @ApiResponse(responseCode = "200", description = "The employee's store.")
    @UnauthenticatedResponse
    @ForbiddenResponse
    @InternalErrorResponse
    EmploymentResponse getMyEmployment() {
        return employment.current();
    }
}
