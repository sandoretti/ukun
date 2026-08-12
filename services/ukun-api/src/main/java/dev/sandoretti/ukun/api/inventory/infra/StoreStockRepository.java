package dev.sandoretti.ukun.api.inventory.infra;

import dev.sandoretti.ukun.api.inventory.domain.StoreStock;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * The same batch, with the number of units instead of the bare identifier — what
     * {@code InventoryApi.sellableOnlineQuantities} answers.
     *
     * <p><strong>It is asked about the sellable products only.</strong> Whether a product is still on
     * sale is not in this table (ADR-0013), so the caller filters the batch before it gets here; this
     * query answers about rows, which is all it can see.
     *
     * <p><strong>Why the quantity travels here and not above.</strong> The javadoc of
     * {@link #availableProductIds} argues that a method returning rows would put the figure one getter
     * away from a public, cacheable response, and that still holds: this one exists because the cart and
     * the checkout attempt <em>are</em> the two places the contract shows the figure, and only to the
     * customer it belongs to. Two methods rather than one shared method used carefully — the caller of
     * the public endpoint has no way to reach the number even by accident.
     *
     * <p>Rows at zero are filtered out for the same reason they are above: a line at zero and no line
     * at all mean the same thing to whoever reads this, and leaving the distinction in would invite
     * somebody to act on it.
     *
     * <p>A closed interface projection rather than {@code Object[]}: the two columns keep their names and
     * their types, and it stays inside {@code infra} — the pair is a shape this query needs, not a
     * concept the rest of the application should learn.
     */
    @Query(
            """
            select stock.productId as productId, stock.quantity as quantity
              from StoreStock stock
             where stock.storeId = :storeId
               and stock.productId in :productIds
               and stock.quantity > 0
            """)
    List<ProductQuantity> quantitiesOf(
            @Param("storeId") Long storeId, @Param("productIds") Collection<Long> productIds);

    /**
     * The lines of an order, locked for update and returned in the order they were locked in — the
     * pessimistic half of ADR-0008, and the query that makes checkout free of defect #2.
     *
     * <p><strong>Rows, not a projection, and this is the one query here that has to return
     * them.</strong> The two reads above argue at length for projecting identifiers and quantities;
     * this one is a read-modify-write and the entity is what carries the modification back.
     *
     * <p><strong>{@code order by stock.id} is not cosmetic and must not be "tidied up".</strong> Two
     * concurrent orders sharing two products in opposite sequence would each hold the row the other
     * is waiting for, and Postgres would resolve it by killing one with a deadlock error — a
     * {@code 500} for a customer who did nothing but buy two things. Locking in a single agreed order
     * makes the cycle unconstructible. The criterion is the surrogate key of ADR-0015 rather than
     * {@code product_id}, and any deterministic order over the same rows would do: what matters is
     * that everybody uses the same one.
     *
     * <p><strong>It is the second of two normalisations, not the only one.</strong> {@code
     * CheckoutAttempt.lines} is mapped {@code @OrderBy("id.productId ASC")}, so confirmations already
     * reach this query with their products in an agreed sequence whatever order the carts were filled
     * in. That is why {@code CheckoutConcurrencyIT} carries no deadlock test — through the API the
     * opposing sequence cannot be built, and its javadoc says so at length. The {@code order by} stays
     * because this query is reachable from anything that decrements stock, not only from an attempt.
     *
     * <p><strong>Rows at zero are <em>not</em> filtered out</strong>, unlike everywhere else in this
     * interface. A line at zero is a line that has to be locked: leaving it out would let the check
     * "is there enough?" run against a row another transaction is topping up at that very moment.
     * The caller reads a missing row and a zero row the same way — no units — but only after both are
     * held.
     *
     * <p><strong>What the lock buys, measured.</strong> Removing it does not oversell — the
     * {@code @Version} of {@code StoreStock} catches the lost update and the second transaction dies
     * with an optimistic failure — but the customer who lost the race then gets a
     * {@code 500 INTERNAL_ERROR} instead of {@code 409 INSUFFICIENT_STOCK}, which is
     * {@code CheckoutConcurrencyIT} going red. The three defences of ADR-0008 are layered on purpose:
     * this lock keeps checkout <em>answerable</em>, the version keeps a stray writer honest, and
     * {@code ck_store_stock_quantity} is the net under both.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select stock
              from StoreStock stock
             where stock.storeId = :storeId
               and stock.productId in :productIds
             order by stock.id
            """)
    List<StoreStock> lockLinesOf(@Param("storeId") Long storeId, @Param("productIds") Collection<Long> productIds);

    /** How many units of one product a store holds. See {@link #quantitiesOf}. */
    interface ProductQuantity {

        Long getProductId();

        int getQuantity();
    }
}
