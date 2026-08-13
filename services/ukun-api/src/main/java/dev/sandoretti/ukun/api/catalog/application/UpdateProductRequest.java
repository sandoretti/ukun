package dev.sandoretti.ukun.api.catalog.application;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * What an admin actually edits about a product (RF-10): its name, its description, its price and its
 * type.
 *
 * <p><strong>Three fields are missing on purpose</strong>, and each absence is a decision the contract
 * spells out:
 *
 * <ul>
 *   <li>no {@code sku} — it is the business identifier the employee searches stock by and the one
 *       printed on the physical label (ADR-0015); rewriting it would silently change which product an
 *       existing label points at. Changing a SKU means creating another product.
 *   <li>no {@code isActive} — discontinuing is a business decision with its own audit trail and its own
 *       operations ({@code :discontinue} / {@code :reactivate}, ADR-0013), not a checkbox that rides
 *       along with a price change.
 *   <li>no image — that is the {@code /image} sub-resource.
 * </ul>
 *
 * <p>Renaming or repricing alters <strong>no past order</strong>: the order line keeps its own snapshot
 * of name and unit price (ADR-0011). It does change the public catalogue immediately, which is the
 * point.
 */
public record UpdateProductRequest(
        @NotBlank @Size(min = 1, max = 255) String name,
        @NotBlank @Size(min = 1) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @NotNull Long typeId) {}
