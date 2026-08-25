package dev.sandoretti.ukun.api.orders.application;

import dev.sandoretti.ukun.api.catalog.ProductReferenceCounter;
import dev.sandoretti.ukun.api.catalog.ProductReferenceKind;
import dev.sandoretti.ukun.api.orders.infra.CustomerOrderRepository;
import org.springframework.stereotype.Component;

/**
 * {@code orders}' half of the reference count of RF-10 — the {@code inOrders} extension of
 * {@code PRODUCT_IN_USE}, and the one that makes the refusal permanent.
 *
 * <p>Stock lines can be removed and carts empty themselves; a sold product stays sold. That is
 * ADR-0013's whole argument for logical discontinuation existing at all: with {@code RESTRICT} on
 * {@code order_line.product_id}, RF-10's delete would otherwise be a requirement that almost nothing
 * can satisfy.
 */
@Component
class OrderProductReferences implements ProductReferenceCounter {

    private final CustomerOrderRepository orders;

    OrderProductReferences(CustomerOrderRepository orders) {
        this.orders = orders;
    }

    @Override
    public ProductReferenceKind kind() {
        return ProductReferenceKind.ORDERS;
    }

    @Override
    public long countReferencesTo(Long productId) {
        return orders.countOrderLinesByProductId(productId);
    }
}
