package dev.sandoretti.ukun.api.inventory.application;

import static dev.sandoretti.ukun.api.inventory.application.Sellability.sellable;

import dev.sandoretti.ukun.api.catalog.ProductSummary;
import dev.sandoretti.ukun.api.inventory.InventoryApi;
import dev.sandoretti.ukun.api.inventory.StockDecrement;
import dev.sandoretti.ukun.api.inventory.domain.StoreStock;
import dev.sandoretti.ukun.api.inventory.infra.StoreStockRepository;
import dev.sandoretti.ukun.api.inventory.infra.StoreStockRepository.ProductQuantity;
import dev.sandoretti.ukun.api.stores.StoresApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * What {@code inventory} answers to the rest of the application: how many units the online store can
 * sell, and the decrement that takes them out when an order is placed.
 *
 * <p><strong>Can sell, not holds</strong>, and the difference is {@link Sellability}: a discontinued
 * product keeps its stock row (ADR-0013) and both methods here treat it as having nothing. The rule
 * used to be applied by the callers instead — issue #122 — which is how a rule of this module came to
 * live in two that do not own it.
 *
 * <p>Both methods resolve the online store themselves rather than taking it as a parameter, which is
 * the same decision stated twice: RF-04 makes selling from the online store — and from nowhere else —
 * a business rule, and this is the module that gets to enforce it. {@code cart} could not pass it in
 * even if it wanted to, since {@code stores} is not among its allowed dependencies (ADR-0017 §1).
 *
 * <p>The class is <strong>not</strong> {@code @Transactional(readOnly = true)} at class level, unlike
 * every query service in this service: half of it writes, and a read-only default with one override
 * would make the writing half depend on nobody deleting an annotation. The read declares its own; the
 * decrement runs in the caller's transaction (see {@link #decrementOnlineStock}).
 *
 * <p>No work in the constructor — rule 6 of the skill {@code spring-api}, defect #6: the legacy
 * resolves its online store from a bean constructor by name and fails to start when the row is
 * missing.
 */
@Service
public class InventoryService implements InventoryApi {

    private final StoreStockRepository stock;
    private final StoresApi stores;

    public InventoryService(StoreStockRepository stock, StoresApi stores) {
        this.stock = stock;
        this.stores = stores;
    }

    /**
     * {@link InventoryApi#sellableOnlineQuantities(Map)}: the batch read of the figure, for the modules
     * that are allowed to show it.
     *
     * <p>Two statements and never more — one to resolve the online store, one for the whole batch —
     * whatever the number of products asked about. The products arrive already read, so applying
     * {@link Sellability} costs nothing: the discontinued ones are dropped from the batch before it is
     * asked for, which is one fewer identifier in the {@code in} clause rather than one more query.
     *
     * <p>An empty request short-circuits before either statement. The empty cart is the commonest case
     * this method sees, and it has nothing to look up. A batch of nothing but discontinued products ends
     * up in the same place, one filter later.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> sellableOnlineQuantities(Map<Long, ProductSummary> products) {
        Set<Long> sellableIds = products.entrySet().stream()
                .filter(product -> sellable(product.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        if (sellableIds.isEmpty()) {
            return Map.of();
        }

        return stock.quantitiesOf(stores.onlineStoreId(), sellableIds).stream()
                .collect(Collectors.toMap(ProductQuantity::getProductId, ProductQuantity::getQuantity));
    }

    /**
     * {@link InventoryApi#decrementOnlineStock(Map, Map)}: the pessimistic, ordered decrement of
     * ADR-0008.
     *
     * <p><strong>What cannot be sold is refused before anything is locked.</strong> A discontinued
     * product keeps its units (ADR-0013) and a deleted one is not in the batch at all; either way there
     * is nothing to take, and finding that out costs no statement because the products came in with the
     * request. Doing it first is what keeps the rejection free of locks nobody needs — and it reproduces
     * the order the caller used to apply by hand: every unsellable line is ruled out before the first
     * question about quantity, so an order short of both reports the discontinued product.
     *
     * <p><strong>{@code MANDATORY}, not {@code REQUIRED}.</strong> The lock is only worth anything for
     * as long as the transaction that took it is open, so a decrement that started a transaction of
     * its own would release every row the instant it returned — and the order would then be written
     * against stock nobody is holding, which is defect #2 restored through the back door. Declaring it
     * mandatory turns "the caller forgot to be transactional" into a startup-shaped failure at the
     * first call instead of a race that shows up in production.
     *
     * <p><strong>Two passes over the same locked rows, and never one.</strong> The first decides, the
     * second writes. Deciding and writing in a single loop would leave the first half of a rejected
     * order already decremented in the persistence context — correct only because the caller happens
     * to roll back, which is a guarantee living in another module's code.
     *
     * <p>The rows are read back into a map keyed by product, but the <em>iteration</em> is over the
     * request: what the caller asked for decides which line a rejection names, and a product with no
     * row at all has to be reachable by iterating the request rather than the result.
     *
     * <p>Nothing is saved explicitly. The entities are managed and inside the caller's transaction, so
     * Hibernate flushes the new quantities at commit — a {@code saveAll} here would only add a
     * statement Spring Data has to translate back into the same update.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public StockDecrement decrementOnlineStock(
            Map<Long, Integer> quantitiesByProductId, Map<Long, ProductSummary> products) {

        if (quantitiesByProductId.isEmpty()) {
            return new StockDecrement.Applied(List.of());
        }

        for (Map.Entry<Long, Integer> requested : quantitiesByProductId.entrySet()) {
            if (!sellable(products.get(requested.getKey()))) {
                return new StockDecrement.Rejected(null, requested.getKey(), 0, requested.getValue());
            }
        }

        Map<Long, StoreStock> locked = stock
                .lockLinesOf(stores.onlineStoreId(), quantitiesByProductId.keySet())
                .stream()
                .collect(Collectors.toMap(StoreStock::getProductId, Function.identity()));

        for (Map.Entry<Long, Integer> requested : quantitiesByProductId.entrySet()) {
            StoreStock line = locked.get(requested.getKey());
            int available = line == null ? 0 : line.getQuantity();

            if (available < requested.getValue()) {
                Long lineId = line == null ? null : line.getId();
                return new StockDecrement.Rejected(lineId, requested.getKey(), available, requested.getValue());
            }
        }

        List<StockDecrement.StockChange> changes = new ArrayList<>(quantitiesByProductId.size());
        quantitiesByProductId.forEach((productId, quantity) -> {
            StoreStock line = locked.get(productId);
            int before = line.getQuantity();
            line.setQuantity(before - quantity);
            changes.add(new StockDecrement.StockChange(productId, before, line.getQuantity()));
        });

        return new StockDecrement.Applied(List.copyOf(changes));
    }
}
