package dev.sandoretti.ukun.api.catalog;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the {@code catalog} module offers the rest of the application. The only type here that other
 * modules may import, alongside {@link ProductSummary} — everything else lives in a subpackage and is
 * invisible to them ({@code docs/14-modules.md} §1).
 *
 * <p>Its callers are the modules that hold a {@code product_id} and need to show a person what it is:
 * {@code cart} today, {@code inventory} for the stock listing of RF-09 and {@code orders} for
 * checkout. None of them owns product data, and none of them navigates to it — the association is a
 * bare {@code Long} on their side (ADR-0017 §2).
 */
public interface CatalogApi {

    /**
     * The products among {@code productIds} that exist, keyed by identifier.
     *
     * <p><strong>Batch, and only batch.</strong> There is deliberately no {@code summaryById}: with one
     * the anti-N+1 rule of ADR-0017 §2 would be a promise kept in code review, and the loop that breaks
     * it is the easiest line in the world to write. This signature makes it impossible to express
     * ({@code docs/14-modules.md} §3).
     *
     * <p>An identifier with no product behind it is <strong>absent from the map</strong>, not a null
     * value and not an exception. The callers all have to handle it anyway — a cart line whose product
     * was deleted, a stock row pointing nowhere — and each of them decides what it means; here it would
     * have to be guessed.
     *
     * <p>Discontinued products <strong>are</strong> included, with {@code active = false}. Filtering
     * them out here would make them indistinguishable from a deleted one, and that difference is
     * exactly what a caller like {@code cart} needs (ADR-0013).
     */
    Map<Long, ProductSummary> summariesByIds(Set<Long> productIds);

    /**
     * The product an employee named by typing its SKU, or empty if there is none they may act on.
     *
     * <p><strong>Only an active product resolves.</strong> A discontinued one answers empty and the
     * caller reports it as not found, which is what stops stock being added to something the shop has
     * taken out of the catalogue (ADR-0013). That decision belongs here rather than in the caller: the
     * moment a second module writes {@code .filter(ProductSummary::active)} for itself, "can this be
     * restocked" has two answers, and only one of them gets fixed.
     *
     * <p>By SKU and not by identifier because that is what the employee has in front of them, printed
     * on the label (ADR-0015). It is unique, so the answer is a product or nothing — never a list.
     */
    Optional<ProductSummary> activeBySku(String sku);
}
