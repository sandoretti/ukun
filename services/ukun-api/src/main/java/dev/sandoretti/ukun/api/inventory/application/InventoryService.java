package dev.sandoretti.ukun.api.inventory.application;

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
 * What {@code inventory} answers to the rest of the application: how many units the online store
 * holds, and the decrement that takes them out when an order is placed.
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
     * {@link InventoryApi#onlineQuantitiesByProductIds(Set)}: the batch read of the figure, for the
     * modules that are allowed to show it.
     *
     * <p>Two statements and never more — one to resolve the online store, one for the whole batch —
     * whatever the number of products asked about.
     *
     * <p>An empty request short-circuits before either statement. The empty cart is the commonest case
     * this method sees, and it has nothing to look up.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> onlineQuantitiesByProductIds(Set<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }

        return stock.quantitiesOf(stores.onlineStoreId(), productIds).stream()
                .collect(Collectors.toMap(ProductQuantity::getProductId, ProductQuantity::getQuantity));
    }

    /**
     * {@link InventoryApi#decrementOnlineStock(Map)}: the pessimistic, ordered decrement of ADR-0008.
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
    public StockDecrement decrementOnlineStock(Map<Long, Integer> quantitiesByProductId) {
        if (quantitiesByProductId.isEmpty()) {
            return new StockDecrement.Applied(List.of());
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
