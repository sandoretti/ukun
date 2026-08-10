package dev.sandoretti.ukun.api.customers.web;

import dev.sandoretti.ukun.api.customers.application.CustomerProfileResponse;
import dev.sandoretti.ukun.api.customers.application.MyProfileService;
import dev.sandoretti.ukun.api.customers.application.UpdateProfileRequest;
import dev.sandoretti.ukun.api.identity.IdentityApi;
import dev.sandoretti.ukun.api.shared.openapi.ApiSecurityScheme;
import dev.sandoretti.ukun.api.shared.openapi.BadRequestResponse;
import dev.sandoretti.ukun.api.shared.openapi.ForbiddenResponse;
import dev.sandoretti.ukun.api.shared.openapi.InternalErrorResponse;
import dev.sandoretti.ukun.api.shared.openapi.NotFoundResponse;
import dev.sandoretti.ukun.api.shared.openapi.UnauthenticatedResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET} and {@code PUT /api/me/profile} (RF-05).
 *
 * <p>A controller of its own rather than two more methods on {@link CurrentUserController}, for the
 * reason {@code ProductTypeController} gives about not being part of {@code ProductController}:
 * {@code /api/me/profile} is a resource of its own, with its own audience and its own authorisation.
 * {@code /api/me} answers "who am I" for anybody with a token; this answers "my profile", which only a
 * customer has.
 *
 * <p>{@code @PreAuthorize("hasRole('CUSTOMER')")} at class level is the {@code 403} the contract
 * declares — an employee reaching here is turned away, and {@code AuthorizationDeniedListener} records
 * it as {@code auth.authorization.denied} without this class doing anything. It is not under
 * {@code /api/admin/**} and must not be: that prefix is for admin-only operations and nothing else
 * (ADR-0020).
 *
 * <p>The {@code PUT} is a {@code PUT} and not a {@code POST} on a verb-shaped path, and there is no
 * mutating {@code GET} anywhere near it — the hard rule of {@code CLAUDE.md}, which the legacy breaks
 * with links that delete.
 */
@RestController
@RequestMapping(path = "/api/me/profile", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('CUSTOMER')")
@SecurityRequirement(name = ApiSecurityScheme.BEARER_AUTH)
class MyProfileController {

    private final MyProfileService profile;
    private final IdentityApi identity;

    MyProfileController(MyProfileService profile, IdentityApi identity) {
        this.profile = profile;
        this.identity = identity;
    }

    @GetMapping
    @ApiResponse(responseCode = "200", description = "The profile.")
    @UnauthenticatedResponse
    @ForbiddenResponse
    @InternalErrorResponse
    CustomerProfileResponse getMyProfile() {
        return profile.view(identity.requireCurrentAccount());
    }

    /**
     * {@code 200} and not {@code 204}: the contract returns the updated profile, which saves the SPA a
     * round trip and makes the response the single source of what was actually stored — including the
     * case where nothing was, because nothing had changed (RF-05).
     */
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponse(responseCode = "200", description = "The updated profile.")
    @BadRequestResponse
    @UnauthenticatedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @InternalErrorResponse
    CustomerProfileResponse updateMyProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return profile.update(identity.requireCurrentAccount(), request);
    }
}
