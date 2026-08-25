package dev.sandoretti.ukun.api.inventory.application;

import dev.sandoretti.ukun.api.catalog.ProductReferenceCounter;
import dev.sandoretti.ukun.api.catalog.ProductReferenceKind;
import dev.sandoretti.ukun.api.inventory.infra.StoreStockRepository;
import org.springframework.stereotype.Component;

/**
 * {@code inventory}'s answer to "does anything of yours point at this product?" — the {@code inStock}
 * half of the {@code 409 PRODUCT_IN_USE} of RF-10.
 *
 * <p>The implementation lives here and the interface lives in {@code catalog} on purpose: this module
 * already depends on that one (ADR-0017 §1) and the reverse edge would close the cycle §3 broke. What
 * crosses the boundary is a number.
 *
 * <p>A stock line is the one of the three references that an admin can clear by hand — deleting the
 * line in each store that carries the product (RF-09) — which is why the response names the three
 * counts separately instead of refusing with a single total.
 */
@Component
class StockProductReferences implements ProductReferenceCounter {

    private final StoreStockRepository stock;

    StockProductReferences(StoreStockRepository stock) {
        this.stock = stock;
    }

    @Override
    public ProductReferenceKind kind() {
        return ProductReferenceKind.STOCK;
    }

    @Override
    public long countReferencesTo(Long productId) {
        return stock.countByProductId(productId);
    }
}
