package dev.sandoretti.ukun.api.cart.infra;

import dev.sandoretti.ukun.api.cart.domain.CartItem;
import dev.sandoretti.ukun.api.cart.domain.CartItemId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@code cart_item}, the table the {@code cart} module owns.
 *
 * <p>Every method here is keyed on {@code customer_id}, which is the leading column of the composite
 * primary key — so all of them are served by {@code pk_cart_item} and none of them needs an index of
 * its own ({@code docs/10-modelo-de-datos.md} §4, which spells out that this is why the table has no
 * {@code ix_cart_item_customer}).
 *
 * <p>There is deliberately no method taking a {@code productId} alone. Every read and every write is
 * scoped to one customer, because a cart line is only ever reachable through its owner — the rule of
 * {@code docs/08-identidad-y-seguridad.md} §4 held up by the shape of the repository rather than by
 * remembering to add a condition.
 */
public interface CartItemRepository extends JpaRepository<CartItem, CartItemId> {

    /**
     * The whole cart of one customer, in a stable order.
     *
     * <p>The {@code ORDER BY} is not decoration: without it the database is free to return the same
     * cart in a different order on two consecutive requests, which makes the list jump around under the
     * cursor for no reason the user can see. It is the same rule as defect #23, one level down —
     * {@code productId} is unique within a cart, so this order is total. Which order to <em>display</em>
     * is the frontend's business; not reshuffling is this method's.
     */
    @Query("select item from CartItem item where item.id.customerId = :customerId order by item.id.productId")
    List<CartItem> findByCustomerId(@Param("customerId") Long customerId);

    /**
     * Empties a cart in one statement — {@code DELETE /api/cart} (RF-03), and the hand-off checkout
     * needs when an order is placed.
     *
     * <p>Bulk and not {@code deleteAll(findByCustomerId(...))}: the second loads every line into the
     * persistence context to issue one {@code DELETE} each, which is the N+1 of a write. The
     * {@code clearAutomatically} keeps the context from serving a stale line afterwards, which matters
     * here because the transaction of {@code POST /api/orders} will go on to do more work after
     * emptying the cart.
     *
     * <p><strong>{@code flushAutomatically} is not decoration, and it was missing.</strong> Clearing a
     * persistence context throws away everything it had not written yet, and checkout calls this method
     * from a transaction holding exactly that: the order's lines, queued behind an identifier the
     * database has already generated, and the decremented stock rows. Without the flush they were
     * silently discarded — an order with no lines and a warehouse that never moved, committed without a
     * single error. Found by {@code PlaceOrderIT}; the pair belongs together on any bulk statement that
     * runs inside somebody else's unit of work.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CartItem item where item.id.customerId = :customerId")
    void deleteByCustomerId(@Param("customerId") Long customerId);

    /**
     * Cart lines pointing at a product, in anybody's cart — the {@code inCarts} count of the {@code 409
     * PRODUCT_IN_USE} of RF-10.
     *
     * <p><strong>The one method here not scoped to a customer, and the exception proves the rule</strong>
     * stated above: what makes a cart line reachable only through its owner is that no method
     * <em>returns</em> one without naming the customer. This returns a count. Nothing about whose cart,
     * or what is in it, leaves this module — it crosses to {@code catalog} through
     * {@code ProductReferenceCounter} as a number, which is the shape that lets the delete say "in two
     * carts" without saying whose.
     */
    @Query("select count(item) from CartItem item where item.id.productId = :productId")
    long countByProductId(@Param("productId") Long productId);
}
