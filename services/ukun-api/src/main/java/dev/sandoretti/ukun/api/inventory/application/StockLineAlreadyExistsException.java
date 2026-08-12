package dev.sandoretti.ukun.api.inventory.application;

import dev.sandoretti.ukun.api.shared.error.ErrorCode;
import dev.sandoretti.ukun.api.shared.error.UkunException;

/**
 * The product already has a stock line in this store, so there is nothing to create — the employee
 * wanted {@code PUT /api/stores/{storeId}/stock/{lineId}}.
 *
 * <p>The use case checks for this before inserting, rather than letting {@code ux_store_stock} raise
 * it: the safety net in {@code GlobalExceptionHandler} only recognises a foreign-key violation, and a
 * unique-key one would come out as a {@code 500}. Checking first is also what lets the message name
 * the product instead of the constraint.
 *
 * <p>The {@code code} is the one the contract declares for this {@code 409} — {@code
 * REFERENTIAL_INTEGRITY}, not {@code DUPLICATE_VALUE}. That second one belongs to v0.7's product CRUD
 * (RF-10, RF-13) and points at a <em>field</em> the admin typed; here what clashes is a row that
 * already relates two things, and the client's move is to edit it, not to change a value.
 */
class StockLineAlreadyExistsException extends UkunException {

    StockLineAlreadyExistsException(String sku) {
        super("Product %s already has a stock line in this store".formatted(sku));
    }

    @Override
    public ErrorCode getCode() {
        return ErrorCode.REFERENTIAL_INTEGRITY;
    }
}
