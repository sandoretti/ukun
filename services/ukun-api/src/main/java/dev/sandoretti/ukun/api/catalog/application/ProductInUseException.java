package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.catalog.ProductReferenceKind;
import dev.sandoretti.ukun.api.shared.error.ErrorCode;
import dev.sandoretti.ukun.api.shared.error.UkunException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The product is referenced and cannot be deleted (RF-10, ADR-0013) — which, for anything that has
 * ever been sold, is permanent: order history is never rewritten, so the {@code RESTRICT} on
 * {@code order_line.product_id} never stops applying. The operational way out is discontinuing it.
 *
 * <p>RF-10 asks for "a clear message" instead of a generic error, and clear means naming the
 * reference: the three counts travel as extensions so the frontend can say <em>where</em> rather than
 * just refusing. That is the whole reason the use case counts before deleting instead of letting
 * Postgres' {@code 23503} reach the user.
 *
 * <p>All three counts are always present, including the zeros. A missing key would be indistinguishable
 * on the client from a zero, and "this product is in two carts and no orders" is precisely the sentence
 * that tells the admin whether waiting is worth anything.
 */
final class ProductInUseException extends UkunException {

    private final Map<ProductReferenceKind, Long> references;

    ProductInUseException(Long productId, Map<ProductReferenceKind, Long> references) {
        super("Product %d is referenced and cannot be deleted".formatted(productId));
        this.references = new EnumMap<>(references);
    }

    @Override
    public ErrorCode getCode() {
        return ErrorCode.PRODUCT_IN_USE;
    }

    /** Keyed by the extension name the registry declares — {@code inOrders}, {@code inStock}, {@code inCarts}. */
    @Override
    public Map<String, Object> getExtensions() {
        Map<String, Object> extensions = new LinkedHashMap<>();
        references.forEach((kind, count) -> extensions.put(kind.extension(), count));

        return extensions;
    }
}
