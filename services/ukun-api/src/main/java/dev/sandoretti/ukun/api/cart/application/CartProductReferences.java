package dev.sandoretti.ukun.api.cart.application;

import dev.sandoretti.ukun.api.cart.infra.CartItemRepository;
import dev.sandoretti.ukun.api.catalog.ProductReferenceCounter;
import dev.sandoretti.ukun.api.catalog.ProductReferenceKind;
import org.springframework.stereotype.Component;

/**
 * {@code cart}'s half of the reference count that decides whether a product can be deleted (RF-10) —
 * the {@code inCarts} extension of {@code PRODUCT_IN_USE}.
 *
 * <p>It is the only one of the three that can clear itself: carts are transient, and a product blocked
 * by cart lines alone becomes deletable as those customers check out or empty them. That is worth
 * knowing when reading the response, and it is why the counts travel separately rather than as a total.
 */
@Component
class CartProductReferences implements ProductReferenceCounter {

    private final CartItemRepository items;

    CartProductReferences(CartItemRepository items) {
        this.items = items;
    }

    @Override
    public ProductReferenceKind kind() {
        return ProductReferenceKind.CARTS;
    }

    @Override
    public long countReferencesTo(Long productId) {
        return items.countByProductId(productId);
    }
}
