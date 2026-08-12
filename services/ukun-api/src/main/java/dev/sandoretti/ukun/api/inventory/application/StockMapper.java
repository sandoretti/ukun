package dev.sandoretti.ukun.api.inventory.application;

import dev.sandoretti.ukun.api.catalog.ProductSummary;
import dev.sandoretti.ukun.api.inventory.domain.StoreStock;
import dev.sandoretti.ukun.api.inventory.infra.StoreStockRepository.StockLineView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@code store_stock} to the contract's {@code StockLine}, from the two shapes this module holds it
 * in (ADR-0009: MapStruct, never a hand-written static factory).
 *
 * <p>The two methods exist because the reads and the writes arrive with different material, not
 * because one is a variant of the other. A listing already carries the product columns in its own
 * projection; a write holds the entity it just saved plus the {@link ProductSummary} it resolved the
 * SKU with. Folding them into one method would mean building a projection by hand after every write
 * just to have something to pass.
 */
@Mapper
public interface StockMapper {

    /** A row of either half of the listing, where the projection's names already match the response. */
    StockLineResponse toResponse(StockLineView view);

    /**
     * The line that was just written, named with the product it points at.
     *
     * <p>{@code ProductSummary} is what {@code catalog} publishes ({@code docs/14-modules.md} §2), so
     * no entity of that module crosses the boundary to get here — and nothing of it reaches the client
     * either: price and image belong to the catalogue screens, not to a stock line.
     */
    @Mapping(target = "lineId", source = "stock.id")
    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "sku", source = "product.sku")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "quantity", source = "stock.quantity")
    @Mapping(target = "version", source = "stock.version")
    StockLineResponse toResponse(StoreStock stock, ProductSummary product);
}
