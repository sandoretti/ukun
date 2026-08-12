package dev.sandoretti.ukun.api.inventory.application;

import static dev.sandoretti.ukun.api.inventory.application.Sellability.sellable;

import dev.sandoretti.ukun.api.catalog.CatalogApi;
import dev.sandoretti.ukun.api.catalog.ProductSummary;
import dev.sandoretti.ukun.api.inventory.infra.StoreStockRepository;
import dev.sandoretti.ukun.api.shared.error.ResourceNotFoundException;
import dev.sandoretti.ukun.api.stores.StoresApi;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * <p><strong>It calls {@code CatalogApi}, and it costs a statement.</strong> An earlier version of this
 * javadoc argued the opposite — the response carries identifiers and booleans, so there is nothing
 * about a product to resolve — and it was right about the response and wrong about the question. What
 * makes a product available is not only its stock row: a discontinued one keeps its units and stops
 * being sellable (ADR-0013), and answering {@code true} for it would be this endpoint contradicting
 * the cart and the checkout, which report it as zero. So the batch is read and {@link Sellability}
 * applied, at the price of one more query on the hottest public read in the shop — the reason issue
 * #122 weighed it rather than assuming it. Nothing else about a product is resolved here, and the
 * response is unchanged in shape.
 *
 * <p>{@code @Transactional(readOnly = true)} at class level and no work in the constructor, like every
 * other query service here (rule 6 of the skill {@code spring-api}, defect #6).
 *
 * <p><strong>It no longer implements {@code InventoryApi}.</strong> Checkout gave this module a
 * writer — the pessimistic decrement of ADR-0008 — and a class whose name ends in {@code QueryService}
 * and whose transaction is {@code readOnly} is not where that belongs. {@link InventoryService} holds
 * the two methods other modules call; this one serves the endpoint of RF-01 and nothing else.
 */
@Service
@Transactional(readOnly = true)
public class AvailabilityQueryService {

    private final StoreStockRepository stock;
    private final StoresApi stores;
    private final CatalogApi catalog;

    public AvailabilityQueryService(StoreStockRepository stock, StoresApi stores, CatalogApi catalog) {
        this.stock = stock;
        this.stores = stores;
        this.catalog = catalog;
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
     * buy here. <strong>A discontinued product with units in the warehouse lands there too</strong>
     * (ADR-0013), which is the same sentence read once more: you cannot buy it here either.
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
        Set<Long> stocked = stock.availableProductIds(storeId, requested);
        Map<Long, ProductSummary> products = catalog.summariesByIds(requested);

        List<AvailabilityItem> items = requested.stream()
                .map(productId -> new AvailabilityItem(
                        productId, stocked.contains(productId) && sellable(products.get(productId))))
                .toList();

        return new AvailabilityResponse(storeId, items);
    }
}
