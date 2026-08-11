package dev.sandoretti.ukun.api.inventory.application;

import dev.sandoretti.ukun.api.inventory.InventoryApi;
import dev.sandoretti.ukun.api.inventory.infra.StoreStockRepository;
import dev.sandoretti.ukun.api.inventory.infra.StoreStockRepository.ProductQuantity;
import dev.sandoretti.ukun.api.shared.error.ResourceNotFoundException;
import dev.sandoretti.ukun.api.stores.StoresApi;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Batch availability of a set of products in one store (RF-01, query Q2 of
 * {@code docs/10-modelo-de-datos.md}).
 *
 * <p><strong>Why this is not part of the catalog response.</strong> {@code catalog} and
 * {@code inventory} would otherwise depend on each other, and ADR-0017 §3 breaks that cycle by
 * choosing a direction ({@code inventory} → {@code catalog}) and splitting the read in two. The
 * frontend fires this request in parallel with {@code GET /api/products} and composes on screen. The
 * intended side effect is worth stating: the catalog becomes cacheable with a long TTL, and
 * availability — the part that changes every time somebody buys something — does not.
 *
 * <p><strong>It calls no {@code CatalogApi}.</strong> The response carries identifiers and booleans,
 * so there is nothing about a product to resolve. The declared {@code catalog} dependency of this
 * module is for the stock listing of RF-09, which does need product names; inventing a batch look-up
 * here to justify it would add a query to the hottest public read in the shop.
 *
 * <p>{@code @Transactional(readOnly = true)} at class level and no work in the constructor, like every
 * other query service here (rule 6 of the skill {@code spring-api}, defect #6).
 */
@Service
@Transactional(readOnly = true)
public class AvailabilityQueryService implements InventoryApi {

    private final StoreStockRepository stock;
    private final StoresApi stores;

    public AvailabilityQueryService(StoreStockRepository stock, StoresApi stores) {
        this.stock = stock;
        this.stores = stores;
    }

    /**
     * One entry per requested identifier, in the order they were asked for.
     *
     * <p><strong>The absent row is the whole point.</strong> A product with no line in
     * {@code store_stock} is {@code available: false}, never a missing entry (RF-01, and the contract
     * repeats it). The three cases the demo data deliberately seeds — a row with units, a row at zero,
     * and no row at all — collapse into the same two-valued answer, so the frontend never has to tell
     * "no data" from "no stock". An identifier that matches no product at all lands in the same place,
     * for the same reason: from the shopper's side, a thing you cannot buy here is a thing you cannot
     * buy here.
     *
     * <p>Duplicates are collapsed and the request order preserved ({@link LinkedHashSet}): asking
     * twice about one product is not two answers, and a response reordered by the database would force
     * the caller to build a map before it could paint a list it already has.
     *
     * <p>The store is validated first and separately. It is the only {@code 404} this endpoint has —
     * the products cannot produce one — and it is a real one: without the check, a typo in
     * {@code ?store=} would come back as a perfectly formed response saying nothing is available
     * anywhere, which is worse than an error because it looks like an answer.
     */
    public AvailabilityResponse availability(Long storeId, List<Long> productIds) {
        if (!stores.exists(storeId)) {
            throw new ResourceNotFoundException("store", storeId);
        }

        Set<Long> requested = new LinkedHashSet<>(productIds);
        Set<Long> available = stock.availableProductIds(storeId, requested);

        List<AvailabilityItem> items = requested.stream()
                .map(productId -> new AvailabilityItem(productId, available.contains(productId)))
                .toList();

        return new AvailabilityResponse(storeId, items);
    }

    /**
     * {@link InventoryApi#onlineQuantitiesByProductIds(Set)}: the same batch read, with the figure, for
     * the modules that are allowed to show it.
     *
     * <p>Two statements and never more — one to resolve the online store, one for the whole batch —
     * whatever the number of products asked about. The store is resolved here rather than taken as a
     * parameter because the caller may not know what an online store is; RF-04 makes it the only store
     * the shop sells from, and this is the module that gets to say so.
     *
     * <p>An empty request short-circuits before either statement. The empty cart is the commonest case
     * this method sees, and it has nothing to look up.
     */
    @Override
    public Map<Long, Integer> onlineQuantitiesByProductIds(Set<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }

        return stock.quantitiesOf(stores.onlineStoreId(), productIds).stream()
                .collect(Collectors.toMap(ProductQuantity::getProductId, ProductQuantity::getQuantity));
    }
}
