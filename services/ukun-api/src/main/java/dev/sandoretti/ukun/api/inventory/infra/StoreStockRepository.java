package dev.sandoretti.ukun.api.inventory.infra;

import dev.sandoretti.ukun.api.inventory.domain.StoreStock;
import java.util.Collection;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@code store_stock}, the table the {@code inventory} module owns.
 *
 * <p>Query Q2 of {@code docs/10-modelo-de-datos.md}: the availability of the products on a catalog
 * page, in one store. Served by {@code ux_store_stock (product_id, store_id)}, which the {@code IN}
 * probes as a set of exact look-ups.
 */
public interface StoreStockRepository extends JpaRepository<StoreStock, Long> {

    /**
     * The identifiers, among those asked for, that this store has in stock right now.
     *
     * <p><strong>It projects identifiers, not rows.</strong> The endpoint answers a boolean per
     * product and must not leak how many units there are — the number is only ever shown to the
     * customer themselves, in their cart and at checkout. A method that returned {@code StoreStock}
     * would put the quantity one getter away from a response that is public and cacheable; this one
     * cannot, whatever the caller does with it.
     *
     * <p>The {@code quantity > 0} test lives in the query rather than in the caller for the same
     * reason: a row at zero is not available, and filtering afterwards is one refactor away from
     * being forgotten.
     *
     * <p>One statement for the whole batch, never one per product — the anti-N+1 shape ADR-0017 §2
     * requires for anything the {@code inventory} module reads in bulk.
     */
    @Query(
            """
            select stock.productId
              from StoreStock stock
             where stock.storeId = :storeId
               and stock.productId in :productIds
               and stock.quantity > 0
            """)
    Set<Long> availableProductIds(@Param("storeId") Long storeId, @Param("productIds") Collection<Long> productIds);
}
