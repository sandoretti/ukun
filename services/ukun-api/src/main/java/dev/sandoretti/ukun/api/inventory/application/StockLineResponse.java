package dev.sandoretti.ukun.api.inventory.application;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A product's stock line in a store, as the contract declares it.
 *
 * <p>{@code lineId}, {@code quantity} and {@code version} are nullable and that is the whole of the
 * {@code NOT_STOCKED} case: the row does not exist yet, so it has no identity, no units and no version
 * to check against. A zero would be a different statement — "this store keeps none of these" — and the
 * screen has to tell the two apart to know whether the next action is a {@code POST} or a {@code PUT}.
 *
 * <p>{@code version} travels out so the employee can send it back in the {@code PUT}: it is the whole
 * of the optimistic concurrency of ADR-0008 as the client sees it.
 */
public record StockLineResponse(
        @Schema(nullable = true) Long lineId,
        Long productId,
        String sku,
        String productName,
        @Schema(nullable = true) Integer quantity,
        @Schema(nullable = true) Long version) {}
