package dev.sandoretti.ukun.api.inventory.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The line an employee is adding to their store: a product named by its SKU, and how many units of it
 * (RF-09).
 *
 * <p><strong>By SKU and not by product identifier</strong>, which is ADR-0015 showing up in the
 * contract: the employee reads the code off the label, and an internal numeric key is not something
 * they have. The pattern is the one the database enforces on {@code product.sku}, repeated here so a
 * malformed code comes back as {@code VALIDATION_FAILED} naming the field instead of as a {@code 404}
 * for a product that could never have existed.
 *
 * <p>{@code quantity} may be zero: a line at zero says "this store keeps this product, and has none
 * right now", which is a different fact from having no line at all.
 */
public record CreateStockLineRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9-]{3,32}$") @Schema(pattern = "^[A-Z0-9-]{3,32}$") String sku,
        @NotNull @Min(0) Integer quantity) {}
