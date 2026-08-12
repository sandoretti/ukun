package dev.sandoretti.ukun.api.inventory.web;

import dev.sandoretti.ukun.api.inventory.application.CreateStockLineRequest;
import dev.sandoretti.ukun.api.inventory.application.StockLineResponse;
import dev.sandoretti.ukun.api.inventory.application.StockService;
import dev.sandoretti.ukun.api.inventory.application.StockStatus;
import dev.sandoretti.ukun.api.inventory.application.UpdateStockLineRequest;
import dev.sandoretti.ukun.api.shared.openapi.ApiSecurityScheme;
import dev.sandoretti.ukun.api.shared.openapi.BadRequestResponse;
import dev.sandoretti.ukun.api.shared.openapi.ConflictResponse;
import dev.sandoretti.ukun.api.shared.openapi.ForbiddenResponse;
import dev.sandoretti.ukun.api.shared.openapi.InternalErrorResponse;
import dev.sandoretti.ukun.api.shared.openapi.NotFoundResponse;
import dev.sandoretti.ukun.api.shared.openapi.UnauthenticatedResponse;
import dev.sandoretti.ukun.api.shared.web.PageResponse;
import dev.sandoretti.ukun.api.shared.web.PaginationParams;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The stock of one store (RF-09). Every employee may manage their own store's; nobody may touch
 * another's.
 *
 * <p><strong>It is not under {@code /api/admin/**} and that is the rule, not an exception</strong>
 * (ADR-0020): that prefix carries the admin-only surface, and stock management is not admin-only. Its
 * authorisation is about a <em>resource</em> — which store — and no route rule can express that, which
 * is why the {@code @PreAuthorize} here only asks for the role and the store check happens in the use
 * case ({@code docs/08-identidad-y-seguridad.md} §4).
 *
 * <p>The line has its own URL rather than a pair of query parameters, which is what ADR-0015 bought
 * with the surrogate key: {@code PUT /api/stores/{storeId}/stock/{lineId}} addresses one row, and the
 * store in the path is checked against the employee instead of being taken at its word.
 */
@RestController
@RequestMapping(path = "/api/stores/{storeId}/stock", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('EMPLOYEE')")
@SecurityRequirement(name = ApiSecurityScheme.BEARER_AUTH)
class StoreStockController {

    private final StockService stock;

    StoreStockController(StockService stock) {
        this.stock = stock;
    }

    /**
     * Either half of the screen, chosen by {@code status}: what the store holds, or what it could hold.
     *
     * <p>{@code status} defaults to {@code STOCKED} in the contract, so the parameter is optional and
     * the default lives here rather than in a caller.
     *
     * <p>{@code @ParameterObject} for the same reason {@code ProductController} carries it: without it
     * springdoc publishes {@code pagination} as one object parameter instead of the {@code page} and
     * {@code size} the contract declares, and {@code OpenApiDriftIT} is what says so.
     */
    @GetMapping
    @ApiResponse(responseCode = "200", description = "A page of stock lines.")
    @BadRequestResponse
    @UnauthenticatedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @InternalErrorResponse
    PageResponse<StockLineResponse> listStoreStock(
            @PathVariable Long storeId,
            @RequestParam(defaultValue = "STOCKED") StockStatus status,
            @RequestParam(required = false) @Pattern(regexp = "^[A-Z0-9-]{3,32}$") String sku,
            @RequestParam(required = false) @Size(min = 1, max = 255) String q,
            @Valid @ParameterObject PaginationParams pagination) {

        return stock.list(storeId, status, sku, q, pagination);
    }

    /**
     * Adds a line for a product this store does not stock yet.
     *
     * <p>{@code Location} points at the line's own URL — the one the {@code PUT} and the {@code DELETE}
     * below use — which is the practical half of giving a stock line an identity of its own (ADR-0015).
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponse(responseCode = "201", description = "Stock line created.")
    @BadRequestResponse
    @UnauthenticatedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @ConflictResponse
    @InternalErrorResponse
    ResponseEntity<StockLineResponse> createStockLine(
            @PathVariable Long storeId, @Valid @RequestBody CreateStockLineRequest request) {

        StockLineResponse created = stock.create(storeId, request);

        return ResponseEntity.created(
                        URI.create("/api/stores/%d/stock/%d".formatted(storeId, created.lineId())))
                .body(created);
    }

    /** Changes the quantity of an existing line, guarded by the {@code version} that was read. */
    @PutMapping(path = "/{lineId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ApiResponse(responseCode = "200", description = "Line updated, carrying the new version.")
    @BadRequestResponse
    @UnauthenticatedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @ConflictResponse
    @InternalErrorResponse
    StockLineResponse updateStockLine(
            @PathVariable Long storeId,
            @PathVariable Long lineId,
            @Valid @RequestBody UpdateStockLineRequest request) {

        return stock.update(storeId, lineId, request);
    }

    /** Removes the line — not the same as setting it to zero. */
    @DeleteMapping("/{lineId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponse(responseCode = "204", description = "Line deleted.")
    @UnauthenticatedResponse
    @ForbiddenResponse
    @NotFoundResponse
    @InternalErrorResponse
    void deleteStockLine(@PathVariable Long storeId, @PathVariable Long lineId) {
        stock.delete(storeId, lineId);
    }
}
