package dev.sandoretti.ukun.api.cart.web;

import dev.sandoretti.ukun.api.cart.application.CartResponse;
import dev.sandoretti.ukun.api.cart.application.CartService;
import dev.sandoretti.ukun.api.cart.application.UpdateCartItemRequest;
import dev.sandoretti.ukun.api.shared.openapi.ApiSecurityScheme;
import dev.sandoretti.ukun.api.shared.openapi.BadRequestResponse;
import dev.sandoretti.ukun.api.shared.openapi.ForbiddenResponse;
import dev.sandoretti.ukun.api.shared.openapi.InternalErrorResponse;
import dev.sandoretti.ukun.api.shared.openapi.NotFoundResponse;
import dev.sandoretti.ukun.api.shared.openapi.UnauthenticatedResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/cart} and {@code /api/cart/items/{productId}} (RF-03).
 *
 * <p><strong>There is no mutating {@code GET} here, and that is the point of the whole slice.</strong>
 * The legacy adds to the cart with {@code GET /carrito/add} and removes with {@code GET
 * /carrito/delete} (defect #3), which means a crawler, a prefetching browser or a chat client
 * unfurling a link can empty somebody's cart. Adding is a {@code PUT} with a body, removing is a
 * {@code DELETE}, and the only {@code GET} reads.
 *
 * <p>The route has <strong>no customer in it</strong>. Whose cart this is comes from the token, resolved
 * server-side in {@link CartService} ({@code docs/08-identidad-y-seguridad.md} §4); the
 * {@code productId} in the path names a line within the caller's own cart and grants nothing.
 *
 * <p>{@code @PreAuthorize("hasRole('CUSTOMER')")} at class level is the {@code 403} the contract
 * declares — an employee holds a perfectly valid token for this service and still has no cart here, the
 * two roles being mutually exclusive (ADR-0006). It is not under {@code /api/admin/**} and must not be:
 * that prefix is for admin-only operations and nothing else (ADR-0020).
 */
@RestController
@RequestMapping(path = "/api/cart", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('CUSTOMER')")
@SecurityRequirement(name = ApiSecurityScheme.BEARER_AUTH)
class CartController {

    private final CartService cart;

    CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping
    @ApiResponse(responseCode = "200", description = "The cart.")
    @UnauthenticatedResponse
    @ForbiddenResponse
    @InternalErrorResponse
    CartResponse getCart() {
        return cart.view();
    }

    /**
     * {@code 204} and no body: there is nothing left to describe, and the caller asked for an outcome
     * rather than for a resource. Idempotent — emptying an already empty cart is a {@code 204} too.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "Cart emptied.")
    @UnauthenticatedResponse
    @ForbiddenResponse
    @InternalErrorResponse
    void clearCart() {
        cart.empty();
    }

    /**
     * {@code 200} with the whole cart and not {@code 204}: the contract returns it so the SPA does not
     * have to recompute the total or chain a second request, which is also what keeps the total the
     * server's answer rather than one the frontend adds up on its own.
     */
    @PutMapping(path = "/items/{productId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponse(responseCode = "200", description = "The updated cart.")
    @BadRequestResponse
    @UnauthenticatedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @InternalErrorResponse
    CartResponse putCartItem(@PathVariable Long productId, @Valid @RequestBody UpdateCartItemRequest request) {
        return cart.putItem(productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "Line removed.")
    @UnauthenticatedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @InternalErrorResponse
    void deleteCartItem(@PathVariable Long productId) {
        cart.removeItem(productId);
    }
}
