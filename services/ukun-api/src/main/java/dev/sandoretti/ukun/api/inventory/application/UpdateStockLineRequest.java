package dev.sandoretti.ukun.api.inventory.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * A new quantity for a line that already exists, together with the {@code version} it was read at.
 *
 * <p><strong>{@code version} is required, and that is what makes the concurrency of ADR-0008
 * optimistic rather than last-write-wins.</strong> Without it the second of two employees editing the
 * same line at the same moment would overwrite the first silently; with it, they get
 * {@code 409 VERSION_CONFLICT} and read the line again. Making it optional would make the guarantee
 * optional too.
 *
 * <p>Zero is a valid quantity. Removing the line is {@code DELETE}, a different operation with a
 * different meaning — the product stops being managed in this store at all.
 */
public record UpdateStockLineRequest(@NotNull @Min(0) Integer quantity, @NotNull Long version) {}
