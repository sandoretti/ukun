package dev.sandoretti.ukun.api.cart.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;

/**
 * The identity of a cart line: the customer and the product, together.
 *
 * <p>It is the primary key of {@code cart_item} in the baseline, and that is a modelling decision
 * rather than a leftover — RF-03 says there is <strong>one line per {@code (customer, product)}</strong>,
 * so a surrogate key would let a second line for the same product exist and leave the rule to be
 * enforced by whoever remembers it. Here the database cannot hold two.
 *
 * <p><strong>Why this one is composite when {@code StoreStock} is not.</strong> ADR-0015 gave stock a
 * surrogate key on purpose, because {@code PUT /api/stores/{storeId}/stock/{lineId}} needs a line that
 * can be addressed by a URL. A cart line has no URL of its own: the contract addresses it as
 * {@code /api/cart/items/{productId}} within the caller's own cart, which is this key minus the half
 * that comes from the token.
 *
 * <p>A class and not a {@code record}: JPA requires an identifier class to be {@link Serializable} and
 * to have a no-argument constructor, and Hibernate's support for records as {@code @EmbeddedId} is not
 * something to discover in production. {@code equals} and {@code hashCode} are written out because an
 * identifier class must have them — the whole of the persistence context's identity map depends on
 * them — and {@code @Data} is banned here for reasons the entity next door explains.
 */
@Embeddable
@Getter
public class CartItemId implements Serializable {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** For JPA only. */
    protected CartItemId() {}

    public CartItemId(Long customerId, Long productId) {
        this.customerId = customerId;
        this.productId = productId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CartItemId id)) {
            return false;
        }
        return Objects.equals(customerId, id.customerId) && Objects.equals(productId, id.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId, productId);
    }

    @Override
    public String toString() {
        return "CartItemId{customerId=%d, productId=%d}".formatted(customerId, productId);
    }
}
