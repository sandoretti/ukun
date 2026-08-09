package dev.sandoretti.ukun.api.inventory.application;

/**
 * One entry of {@code GET /api/availability} — the {@code AvailabilityItem} schema of the contract.
 *
 * <p>Two fields, and the second is a boolean rather than a count on purpose: how many units a store
 * holds is revealed to the customer only in their own cart and at checkout, never on a public read
 * (RF-01, and the contract says so in as many words).
 */
public record AvailabilityItem(Long productId, boolean available) {}
