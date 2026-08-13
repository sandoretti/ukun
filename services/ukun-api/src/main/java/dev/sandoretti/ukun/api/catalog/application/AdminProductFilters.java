package dev.sandoretti.ukun.api.catalog.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The filters and the sort criterion of {@code GET /api/admin/products} (RF-10). All of them optional:
 * one that was not sent is not applied.
 *
 * <p>It is a different object from {@link ProductFilters} because the two listings ask different
 * questions. The shop filters by price range; the back-office filters by SKU and by whether the product
 * is still in the catalogue, and has no use for a price band. Sharing one record would have meant
 * publishing {@code priceMin} on a screen nobody filters prices from, and {@code status} on a public
 * endpoint that must never show a discontinued product.
 *
 * <p>{@code sku} is an exact match and not a prefix: the admin who types one has read it off a label.
 * Unlike {@code GET /api/products/by-sku/{sku}}, it resolves discontinued products too — which is the
 * point of this listing.
 *
 * <p>Like every other filter object of this API it lives next to the use case rather than in
 * {@code web/}: it is the input of the query, not a detail of HTTP.
 */
public record AdminProductFilters(
        @Size(min = 1, max = 255) String q,
        @Pattern(regexp = "^[A-Z0-9-]{3,32}$") @Schema(pattern = "^[A-Z0-9-]{3,32}$") String sku,
        Long typeId,
        ProductStatusFilter status,
        AdminProductSort sort) {

    public AdminProductFilters {
        status = status == null ? ProductStatusFilter.DEFAULT : status;
        sort = sort == null ? AdminProductSort.DEFAULT : sort;
    }
}
