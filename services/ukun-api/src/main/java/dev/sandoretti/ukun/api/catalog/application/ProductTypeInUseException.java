package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.shared.error.ErrorCode;
import dev.sandoretti.ukun.api.shared.error.UkunException;
import java.util.Map;

/**
 * The type still has products, so it cannot be deleted (RF-13, ADR-0013). The way out is reassigning
 * those products to another type — there is no logical deletion for a type, and ADR-0013 explains why
 * it would be a state nothing could ever leave.
 *
 * <p>The count comes from the use case, which asks for it <em>before</em> attempting the delete. The
 * foreign key is {@code RESTRICT} and would refuse it anyway; what the check buys is the number the
 * client is told, since a {@code 23503} can only say that something referenced the row.
 */
final class ProductTypeInUseException extends UkunException {

    private final long inProducts;

    ProductTypeInUseException(Long typeId, long inProducts) {
        super("Product type %d still has %d product(s)".formatted(typeId, inProducts));
        this.inProducts = inProducts;
    }

    @Override
    public ErrorCode getCode() {
        return ErrorCode.PRODUCT_TYPE_IN_USE;
    }

    @Override
    public Map<String, Object> getExtensions() {
        return Map.of("inProducts", inProducts);
    }
}
