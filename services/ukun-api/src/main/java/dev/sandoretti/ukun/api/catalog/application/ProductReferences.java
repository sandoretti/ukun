package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.catalog.ProductReferenceCounter;
import dev.sandoretti.ukun.api.catalog.ProductReferenceKind;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The three counters of {@link ProductReferenceCounter} seen as one question: "what still points at
 * this product?".
 *
 * <p>Spring injects every implementation it finds, one per module that owns a table with a
 * {@code product_id}. This class is what turns that list into the closed, complete set the
 * {@code PRODUCT_IN_USE} body promises — every kind present, every count a number.
 *
 * <p><strong>A missing implementation is a startup failure, not a zero.</strong> That is the whole
 * point of checking here: a module that stopped registering its counter would otherwise make the delete
 * quietly more permissive, the database would refuse it with a bare {@code 23503}, and the response
 * would say the product is referenced nowhere while refusing to delete it for being referenced. The
 * check is in-memory and touches no data, so it is not the constructor query rule 6 forbids.
 */
@Component
class ProductReferences {

    private final Map<ProductReferenceKind, ProductReferenceCounter> counters;

    ProductReferences(List<ProductReferenceCounter> counters) {
        this.counters = counters.stream()
                .collect(Collectors.toMap(
                        ProductReferenceCounter::kind,
                        Function.identity(),
                        ProductReferences::rejectDuplicate,
                        () -> new EnumMap<>(ProductReferenceKind.class)));

        for (ProductReferenceKind kind : ProductReferenceKind.values()) {
            if (!this.counters.containsKey(kind)) {
                throw new IllegalStateException("No ProductReferenceCounter registered for " + kind);
            }
        }
    }

    /**
     * All three counts, in the order of the enum so the response reads the same way every time.
     *
     * <p>Three statements, always, even when the first one already blocks the delete. Short-circuiting
     * would answer "in one order" and leave the other two unknown, and the number the admin acts on is
     * the full picture — whether waiting for the carts to clear is worth anything depends on the orders
     * count being zero.
     */
    Map<ProductReferenceKind, Long> countFor(Long productId) {
        Map<ProductReferenceKind, Long> counts = new EnumMap<>(ProductReferenceKind.class);
        counters.forEach((kind, counter) -> counts.put(kind, counter.countReferencesTo(productId)));

        return counts;
    }

    private static ProductReferenceCounter rejectDuplicate(
            ProductReferenceCounter first, ProductReferenceCounter second) {

        throw new IllegalStateException("Two ProductReferenceCounters answer for " + first.kind());
    }
}
