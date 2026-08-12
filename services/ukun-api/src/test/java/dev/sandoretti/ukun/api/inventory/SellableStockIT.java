package dev.sandoretti.ukun.api.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.api.catalog.CatalogApi;
import dev.sandoretti.ukun.api.catalog.ProductSummary;
import dev.sandoretti.ukun.api.stores.StoresIntegrationTest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The rule of ADR-0013 as {@code inventory} answers it: <strong>a discontinued product has nothing to
 * sell, however many units its row holds.</strong>
 *
 * <p><strong>Why this suite exists</strong> (issue #122). The rule was already covered — three times,
 * from outside: {@code CartIT} asserts the line reports zero, {@code CheckoutAttemptIT} asserts the
 * attempt does, {@code PlaceOrderIT} asserts the order is refused. All three still pass and none of
 * them was touched, which is what proves this refactor moved nothing. What none of them showed is
 * <em>where</em> the rule lives, and until now the honest answer was "in the two modules that call
 * this one". This is the test that makes the owner obvious to whoever reads it next.
 *
 * <p>It drives {@link InventoryApi} directly rather than an endpoint, because the API is the boundary
 * under test: the callers hand over what they read from {@code CatalogApi} and take the answer as it
 * comes. The products are read through the real {@code CatalogApi} for that same reason — a
 * hand-built {@code ProductSummary} would assert against the fixture instead of against the catalogue.
 *
 * <p>The decrement runs inside {@link #inATransaction}: it is {@code MANDATORY} on purpose (ADR-0008),
 * because a lock released when the method returns would be defect #2 through the back door.
 */
class SellableStockIT extends StoresIntegrationTest {

    private static final String ON_SALE = "SOFA-CUERO";
    private static final String DISCONTINUED = "SILLA-RETIRADA";

    private static final int UNITS = 4;

    @Autowired
    private InventoryApi inventory;

    @Autowired
    private CatalogApi catalog;

    /** Both products stocked online, and identically: the only difference between them is {@code is_active}. */
    @BeforeEach
    void seedTwoStockedProducts() {
        jdbc.update("INSERT INTO product_type (name) VALUES ('Sofás')");

        ProductRows.insert(jdbc, ON_SALE, "Sofá de cuero", "399.99", true);
        ProductRows.insert(jdbc, DISCONTINUED, "Silla retirada", "399.99", false);

        long online = storeIdOf("Ukun Online");

        inATransaction(() -> {
            entityManager.persist(StoreStockMother.stock(productIdOf(ON_SALE), online, UNITS));
            entityManager.persist(StoreStockMother.stock(productIdOf(DISCONTINUED), online, UNITS));
        });
    }

    @AfterEach
    void clearTheStock() {
        jdbc.execute("TRUNCATE TABLE store_stock, product, product_type RESTART IDENTITY CASCADE");
    }

    /**
     * The two rows are the same row but for one boolean, and the answers differ. Asserted together
     * rather than in two tests because the pair <em>is</em> the rule: without the product still on sale
     * reporting its units, "reports zero" would also pass on an implementation that reports zero for
     * everything.
     */
    @Test
    @DisplayName("a discontinued product reports no units, and the one still on sale reports its own")
    void reportsNothingSellableForADiscontinuedProduct() {
        Map<Long, Integer> sellable = inventory.sellableOnlineQuantities(summariesOf(ON_SALE, DISCONTINUED));

        assertThat(sellable).containsOnly(Map.entry(productIdOf(ON_SALE), UNITS));
    }

    /**
     * The same rule on the operation that takes the money. {@code stockLineId} is {@code null} and
     * {@code available} is zero — the row is never even looked at, so there is no line to name — which
     * is exactly the shape {@code orders} turns into {@code 409 INSUFFICIENT_STOCK}
     * ({@code docs/12-error-contract.md}).
     */
    @Test
    @DisplayName("a discontinued product is refused by the decrement, with no stock line named")
    void refusesToDecrementADiscontinuedProduct() {
        long discontinued = productIdOf(DISCONTINUED);

        StockDecrement decrement = decrement(Map.of(discontinued, 1), DISCONTINUED);

        assertThat(decrement).isEqualTo(new StockDecrement.Rejected(null, discontinued, 0, 1));
        assertThat(unitsOf(DISCONTINUED)).isEqualTo(UNITS);
    }

    /**
     * The unsellable line is ruled out before any question about quantity, whatever order the request
     * lists them in. It is the order the caller used to apply by hand, and the reason it matters is that
     * it decides which product the customer is told about: an order short of both should name the one
     * that is no longer sold, not the one that could be restocked.
     */
    @Test
    @DisplayName("with one product discontinued and another short of units, the discontinued one is named")
    void refusesTheUnsellableLineBeforeTheShortOne() {
        Map<Long, Integer> tooMuchOfBoth = new LinkedHashMap<>();
        tooMuchOfBoth.put(productIdOf(ON_SALE), UNITS + 1);
        tooMuchOfBoth.put(productIdOf(DISCONTINUED), UNITS + 1);

        StockDecrement decrement = decrement(tooMuchOfBoth, ON_SALE, DISCONTINUED);

        assertThat(decrement)
                .isEqualTo(new StockDecrement.Rejected(null, productIdOf(DISCONTINUED), 0, UNITS + 1));
    }

    /** Nothing above says the decrement stopped working: the product still on sale gives up its units. */
    @Test
    @DisplayName("a product still on sale is decremented as before")
    void stillDecrementsAProductOnSale() {
        StockDecrement decrement = decrement(Map.of(productIdOf(ON_SALE), 3), ON_SALE);

        assertThat(decrement)
                .isEqualTo(new StockDecrement.Applied(
                        List.of(new StockDecrement.StockChange(productIdOf(ON_SALE), UNITS, 1))));
        assertThat(unitsOf(ON_SALE)).isEqualTo(1);
    }

    /** Runs the decrement in a committed transaction of its own, as a confirmation does. */
    private StockDecrement decrement(Map<Long, Integer> quantities, String... skus) {
        AtomicReference<StockDecrement> result = new AtomicReference<>();
        inATransaction(() -> result.set(inventory.decrementOnlineStock(quantities, summariesOf(skus))));

        return result.get();
    }

    /** What a caller would have read from {@code CatalogApi} before asking. */
    private Map<Long, ProductSummary> summariesOf(String... skus) {
        Set<Long> productIds =
                Arrays.stream(skus).map(this::productIdOf).collect(Collectors.toSet());

        return catalog.summariesByIds(productIds);
    }

    private int unitsOf(String sku) {
        return jdbc.queryForObject(
                "SELECT quantity FROM store_stock WHERE product_id = ?", Integer.class, productIdOf(sku));
    }
}
