package dev.sandoretti.ukun.api.orders.web;

import dev.sandoretti.ukun.api.orders.application.CheckoutService;
import dev.sandoretti.ukun.api.orders.application.OrderFilters;
import dev.sandoretti.ukun.api.orders.application.OrderQueryService;
import dev.sandoretti.ukun.api.orders.application.OrderResponse;
import dev.sandoretti.ukun.api.orders.application.PlacedOrder;
import dev.sandoretti.ukun.api.shared.openapi.ApiSecurityScheme;
import dev.sandoretti.ukun.api.shared.openapi.BadRequestResponse;
import dev.sandoretti.ukun.api.shared.openapi.ConflictResponse;
import dev.sandoretti.ukun.api.shared.openapi.ForbiddenResponse;
import dev.sandoretti.ukun.api.shared.openapi.GoneResponse;
import dev.sandoretti.ukun.api.shared.openapi.InternalErrorResponse;
import dev.sandoretti.ukun.api.shared.openapi.NotFoundResponse;
import dev.sandoretti.ukun.api.shared.openapi.UnauthenticatedResponse;
import dev.sandoretti.ukun.api.shared.openapi.UnprocessableContentResponse;
import dev.sandoretti.ukun.api.shared.web.PageResponse;
import dev.sandoretti.ukun.api.shared.web.PaginationParams;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/orders} (RF-04): confirming a checkout, and reading what came of it.
 *
 * <p><strong>Confirming is a {@code POST} with no body.</strong> The attempt opened by
 * {@code POST /api/checkout/attempts} <em>is</em> the request, and the {@code Idempotency-Key} header
 * identifies it (ADR-0016) — so there is nothing for the client to send and nothing for the server to
 * take on trust. The legacy places its orders with {@code GET /pedidos/hacer} (defect #3), which any
 * crawler or prefetching browser can trigger; there is no mutating {@code GET} anywhere in this class.
 *
 * <p><strong>No route here carries a customer.</strong> Whose orders these are comes from the token
 * ({@code docs/08-identidad-y-seguridad.md} §4), and the {@code id} of {@code GET /api/orders/{id}}
 * names an order within the caller's own history: somebody else's is a {@code 404}, resolved in the
 * query rather than by a comparison afterwards.
 *
 * <p>{@code @PreAuthorize("hasRole('CUSTOMER')")} at class level, like the cart and for the same
 * reason (ADR-0006). It is not under {@code /api/admin/**} and must not be (ADR-0020).
 */
@RestController
@RequestMapping(path = "/api/orders", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('CUSTOMER')")
@SecurityRequirement(name = ApiSecurityScheme.BEARER_AUTH)
class OrderController {

    private final OrderQueryService orders;
    private final CheckoutService checkout;

    OrderController(OrderQueryService orders, CheckoutService checkout) {
        this.orders = orders;
        this.checkout = checkout;
    }

    @GetMapping
    @ApiResponse(responseCode = "200", description = "A page of orders.")
    @BadRequestResponse
    @UnauthenticatedResponse
    @ForbiddenResponse
    @InternalErrorResponse
    PageResponse<OrderResponse> listOrders(
            @Valid @ParameterObject OrderFilters filters, @Valid @ParameterObject PaginationParams pagination) {

        return orders.list(filters, pagination);
    }

    /**
     * Confirms the attempt named by {@code Idempotency-Key}.
     *
     * <p><strong>Two success statuses, and the difference is the whole feature.</strong> The first
     * request creates the order and answers {@code 201} with a {@code Location}; every later one — a
     * double click, a retry after a timeout, an F5 on the confirmation screen — answers {@code 200}
     * with the same body and creates nothing. Which of the two this is comes from
     * {@link PlacedOrder}, because it is the idempotency record that knows and not the shape of the
     * response.
     *
     * <p>{@code ResponseEntity} rather than {@code @ResponseStatus} for exactly that reason: the
     * status is decided per request. The {@code Location} is only sent with the {@code 201}, following
     * the contract — a replay is not a creation, and the client already has the URI.
     *
     * <p>The header is typed as a {@code UUID}, so a malformed one is a {@code 400} from Spring's own
     * conversion before any of this runs. That is the right answer: a key that is not a key was never
     * issued by this server.
     */
    @PostMapping
    @ApiResponse(responseCode = "201", description = "Order created. The cart is left empty.")
    @ApiResponse(responseCode = "200", description = "Retry with an already completed key: the original response.")
    @UnauthenticatedResponse
    @ForbiddenResponse
    @ConflictResponse
    @GoneResponse
    @UnprocessableContentResponse
    @InternalErrorResponse
    ResponseEntity<OrderResponse> placeOrder(@RequestHeader("Idempotency-Key") UUID idempotencyKey) {
        PlacedOrder placed = checkout.placeOrder(idempotencyKey);

        if (!placed.created()) {
            return ResponseEntity.ok(placed.order());
        }

        return ResponseEntity.created(URI.create("/api/orders/" + placed.order().id()))
                .body(placed.order());
    }

    /**
     * One order, with its lines and its immutable snapshots — where the {@code Location} of a freshly
     * created order points, and what the confirmation screen reads.
     *
     * <p>There is no {@code if (order == null)}: the service throws
     * {@link dev.sandoretti.ukun.api.shared.error.ResourceNotFoundException} and the advice renders it
     * (defect #11).
     */
    @GetMapping("/{id}")
    @ApiResponse(responseCode = "200", description = "The order.")
    @UnauthenticatedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @InternalErrorResponse
    OrderResponse getOrder(@PathVariable Long id) {
        return orders.detail(id);
    }
}
