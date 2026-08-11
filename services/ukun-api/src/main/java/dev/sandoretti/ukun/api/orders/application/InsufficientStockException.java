package dev.sandoretti.ukun.api.orders.application;

import dev.sandoretti.ukun.api.shared.error.ErrorCode;
import dev.sandoretti.ukun.api.shared.error.UkunException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code INSUFFICIENT_STOCK} (409): a line of the order cannot be served from the online store, so
 * none of it is (RF-04 — no partial orders, no backorders).
 *
 * <p>It names one line, with the five extensions the registry pins: {@code stockLineId},
 * {@code productId}, {@code sku}, {@code available} and {@code requested}. The frontend interpolates
 * them into the message the customer reads, which is why {@code sku} is there at all — an identifier
 * means nothing on screen, and the SKU is what the shop and the customer both call the thing.
 *
 * <p>{@code stockLineId} is null when the product has no stock line in the online store. The
 * extension is then absent rather than {@code null}: a key whose value is null is a shape the frontend
 * has to test for twice, and the registry types it as optional.
 *
 * <p><strong>Reaching this exception means the pessimistic lock did its job</strong>, not that
 * something went wrong: the rows were held, the arithmetic was checked against them, and the answer
 * was no. The failure this replaces is defect #2, where the same situation quietly oversold.
 */
public final class InsufficientStockException extends UkunException {

    private final Long stockLineId;
    private final Long productId;
    private final String sku;
    private final int available;
    private final int requested;

    public InsufficientStockException(Long stockLineId, Long productId, String sku, int available, int requested) {
        super("Product %s has %d units available and %d were requested".formatted(sku, available, requested));
        this.stockLineId = stockLineId;
        this.productId = productId;
        this.sku = sku;
        this.available = available;
        this.requested = requested;
    }

    @Override
    public ErrorCode getCode() {
        return ErrorCode.INSUFFICIENT_STOCK;
    }

    @Override
    public Map<String, Object> getExtensions() {
        Map<String, Object> extensions = new LinkedHashMap<>();
        if (stockLineId != null) {
            extensions.put("stockLineId", stockLineId);
        }
        extensions.put("productId", productId);
        extensions.put("sku", sku);
        extensions.put("available", available);
        extensions.put("requested", requested);

        return extensions;
    }
}
