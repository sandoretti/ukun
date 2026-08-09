package dev.sandoretti.ukun.api.inventory.application;

import java.util.List;

/**
 * The body of {@code GET /api/availability} — the {@code AvailabilityResponse} schema of the contract.
 *
 * <p>{@code storeId} is echoed back because the frontend fires this request in parallel with
 * {@code GET /api/products} and composes the two on screen (ADR-0017 §3): a response that did not say
 * which store it is about would be impossible to match against the right column once two of them are
 * in flight.
 *
 * <p>{@code items} carries one entry per requested identifier, always — see
 * {@link AvailabilityQueryService}.
 */
public record AvailabilityResponse(Long storeId, List<AvailabilityItem> items) {}
