package dev.sandoretti.ukun.api.customers.web;

import dev.sandoretti.ukun.api.customers.application.CurrentUserResponse;
import dev.sandoretti.ukun.api.customers.application.CurrentUserService;
import dev.sandoretti.ukun.api.identity.IdentityApi;
import dev.sandoretti.ukun.api.shared.openapi.ApiSecurityScheme;
import dev.sandoretti.ukun.api.shared.openapi.InternalErrorResponse;
import dev.sandoretti.ukun.api.shared.openapi.UnauthenticatedResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/me}: the session probe the SPA calls right after login to learn who it is talking to
 * and what it may show ({@code docs/01-arquitectura.md} §2).
 *
 * <p>No {@code @PreAuthorize}, and that is the whole of its authorisation: it serves customers and
 * employees alike, and {@code anyRequest().authenticated()} in {@code SecurityConfig} already turns
 * away anybody without a token. A role check here would be a second rule saying "anyone", which is not
 * a rule.
 *
 * <p>It takes no parameter — there is nothing in the URL to identify a person with, by design. Whose
 * identity is answered is decided by the token and only by it ({@code docs/08-identidad-y-seguridad.md}
 * §4).
 */
@RestController
@RequestMapping(path = "/api/me", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = ApiSecurityScheme.BEARER_AUTH)
class CurrentUserController {

    private final CurrentUserService currentUser;
    private final IdentityApi identity;

    CurrentUserController(CurrentUserService currentUser, IdentityApi identity) {
        this.currentUser = currentUser;
        this.identity = identity;
    }

    /** The method name is the {@code operationId} springdoc publishes; the SPA's client is named after it. */
    @GetMapping
    @ApiResponse(responseCode = "200", description = "The current identity.")
    @UnauthenticatedResponse
    @InternalErrorResponse
    CurrentUserResponse getCurrentUser() {
        return currentUser.current(identity.requireCurrentAccount());
    }
}
