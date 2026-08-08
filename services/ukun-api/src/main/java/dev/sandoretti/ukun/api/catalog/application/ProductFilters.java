package dev.sandoretti.ukun.api.catalog.application;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * The four optional filters and the sort criterion of {@code GET /api/products}, with the bounds the
 * contract declares. All four are nullable: a filter that was not sent is not applied.
 *
 * <p>It lives next to the use case rather than in {@code web/} because it is the input of the query,
 * not a detail of HTTP — the controller only binds it. That keeps the dependency running
 * {@code web} → {@code application} and never the other way.
 *
 * <p>{@code priceMin}/{@code priceMax} mirror the {@code Money} schema: at most eight integer digits
 * and two decimals, never negative. They are read as {@code BigDecimal} for the same reason the
 * column is {@code DECIMAL(10,2)} — money is never a {@code Double} (defect #5).
 */
public record ProductFilters(
        @Size(min = 1, max = 255) String name,
        Long typeId,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal priceMin,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal priceMax,
        ProductSort sort) {

    public ProductFilters {
        sort = sort == null ? ProductSort.DEFAULT : sort;
    }
}
