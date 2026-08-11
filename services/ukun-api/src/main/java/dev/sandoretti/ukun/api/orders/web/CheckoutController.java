package dev.sandoretti.ukun.api.orders.web;

import dev.sandoretti.ukun.api.orders.application.CheckoutAttemptResponse;
import dev.sandoretti.ukun.api.orders.application.CheckoutService;
import dev.sandoretti.ukun.api.shared.openapi.ApiSecurityScheme;
import dev.sandoretti.ukun.api.shared.openapi.ForbiddenResponse;
import dev.sandoretti.ukun.api.shared.openapi.InternalErrorResponse;
import dev.sandoretti.ukun.api.shared.openapi.UnauthenticatedResponse;
import dev.sandoretti.ukun.api.shared.openapi.UnprocessableContentResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/checkout/attempts} (ADR-0016): opening a checkout.
 *
 * <p><strong>It has no request body, and that is the design.</strong> What is being checked out is the
 * caller's cart, which the server can already read; a body would be the client telling the server what
 * it is buying, and then the server would have to decide how much of that to believe.
 *
 * <p>A {@code POST} that creates something is the ordinary reading of this route, and it is worth
 * saying out loud next to a legacy that places orders with {@code GET /pedidos/hacer} (defect #3). It
 * is deliberately <strong>not</strong> idempotent: asking twice opens two attempts, each with its own
 * key, and neither of them charges anybody. Idempotency belongs one step later, where the money is.
 *
 * <p>{@code @PreAuthorize("hasRole('CUSTOMER')")}, like the cart: an employee holds a perfectly valid
 * token for this service and has no cart to check out, the two roles being mutually exclusive
 * (ADR-0006).
 */
@RestController
@RequestMapping(path = "/api/checkout", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('CUSTOMER')")
@SecurityRequirement(name = ApiSecurityScheme.BEARER_AUTH)
class CheckoutController {

    private final CheckoutService checkout;

    CheckoutController(CheckoutService checkout) {
        this.checkout = checkout;
    }

    /**
     * {@code 201} with the attempt, and no {@code Location}: the contract declares none, and there is
     * no {@code GET /api/checkout/attempts/{id}} to point at. An attempt is not a resource the client
     * re-reads — it is a quote it either confirms or lets expire.
     */
    @PostMapping("/attempts")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(responseCode = "201", description = "Attempt opened.")
    @UnauthenticatedResponse
    @ForbiddenResponse
    @UnprocessableContentResponse
    @InternalErrorResponse
    CheckoutAttemptResponse createCheckoutAttempt() {
        return checkout.openAttempt();
    }
}
