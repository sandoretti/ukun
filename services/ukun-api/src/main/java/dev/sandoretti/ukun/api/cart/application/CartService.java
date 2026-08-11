package dev.sandoretti.ukun.api.cart.application;

import dev.sandoretti.ukun.api.cart.CartApi;
import dev.sandoretti.ukun.api.cart.CartLineSnapshot;
import dev.sandoretti.ukun.api.cart.domain.CartItem;
import dev.sandoretti.ukun.api.cart.domain.CartItemId;
import dev.sandoretti.ukun.api.cart.infra.CartItemRepository;
import dev.sandoretti.ukun.api.catalog.CatalogApi;
import dev.sandoretti.ukun.api.catalog.ProductSummary;
import dev.sandoretti.ukun.api.customers.CustomersApi;
import dev.sandoretti.ukun.api.inventory.InventoryApi;
import dev.sandoretti.ukun.api.shared.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The four operations of {@code /api/cart} (RF-03).
 *
 * <p><strong>Nothing here takes an identifier that says whose cart it is.</strong> The customer is
 * resolved from the token through {@code CustomersApi} on every call, which is the rule of
 * {@code docs/08-identidad-y-seguridad.md} §4 — the {@code productId} in the path identifies a line
 * <em>within the caller's own cart</em> and authorises nothing. The legacy antipattern is taking an
 * identifier off the request and believing it.
 *
 * <p><strong>Reading the cart is three statements, whatever its size.</strong> The lines, one batch call
 * to {@code CatalogApi} and one to {@code InventoryApi} — never a look-up per line, which is what the
 * batch-only signatures of both APIs make impossible to write (ADR-0017 §2). It is worth being explicit
 * about because the cart is the first read in this service that composes three modules, and a loop here
 * would be defect #12 with a new address.
 *
 * <p>{@code @Transactional(readOnly = true)} at class level with the three writers overriding it, and a
 * constructor that only assigns — rule 6 of the skill {@code spring-api}, and defect #6.
 */
@Service
@Transactional(readOnly = true)
public class CartService implements CartApi {

    private final CartItemRepository items;
    private final CatalogApi catalog;
    private final InventoryApi inventory;
    private final CustomersApi customers;

    public CartService(
            CartItemRepository items, CatalogApi catalog, InventoryApi inventory, CustomersApi customers) {
        this.items = items;
        this.catalog = catalog;
        this.inventory = inventory;
        this.customers = customers;
    }

    /** {@code GET /api/cart}. */
    public CartResponse view() {
        return cartOf(customers.requireCurrentCustomerId());
    }

    /**
     * {@code PUT /api/cart/items/{productId}}: add the product, or set how many of it the line holds.
     *
     * <p>One line per {@code (customer, product)} (RF-03), so this is an upsert and not two endpoints:
     * the row is found or created, and its quantity is replaced by the one that arrived — never added
     * to. Sending the same request twice leaves the same cart, which is what lets the SPA retry it.
     *
     * <p><strong>Availability is reported, not enforced.</strong> Asking for 999 units of a product with
     * three in stock is accepted and comes back saying {@code available: 3}. That is RF-03's "do not
     * carry over" read exactly: the gap in the legacy is that it never <em>tells</em> you, not that it
     * lets you. Stock is checked, and reserved, when the order is placed (RF-04) — doing it here would
     * be a reservation nobody releases.
     *
     * <p>A product that does not exist, or that has been discontinued, is a {@code 404}: it is out of the
     * public catalogue (ADR-0013), so as far as this API is concerned there is nothing to add. The same
     * judgement {@code ProductQueryService.detail} already makes for the product page.
     */
    @Transactional
    public CartResponse putItem(Long productId, int quantity) {
        Long customerId = customers.requireCurrentCustomerId();

        ProductSummary product = catalog.summariesByIds(Set.of(productId)).get(productId);
        if (product == null || !product.active()) {
            throw new ResourceNotFoundException("Product", productId);
        }

        CartItemId id = new CartItemId(customerId, productId);
        CartItem item = items.findById(id).orElseGet(() -> new CartItem(id));
        item.setQuantity(quantity);
        items.save(item);

        return cartOf(customerId);
    }

    /**
     * {@code DELETE /api/cart/items/{productId}}: remove one line.
     *
     * <p>A line that is not there is a {@code 404}, and the asymmetry with {@link #empty()} is the
     * contract's, deliberately: emptying a cart asks for an outcome — no lines — and gets it either way,
     * while removing <em>this</em> line asks about something that has to exist for the request to mean
     * anything. Answering {@code 204} to both would hide a stale page sending deletes for a cart that was
     * emptied in another tab.
     */
    @Transactional
    public void removeItem(Long productId) {
        CartItemId id = new CartItemId(customers.requireCurrentCustomerId(), productId);

        if (!items.existsById(id)) {
            throw new ResourceNotFoundException("CartItem", productId);
        }

        items.deleteById(id);
    }

    /**
     * {@code DELETE /api/cart}: empty the cart, in one statement and one operation.
     *
     * <p>The operation the legacy does not have — there, the only ways to empty a cart are deleting the
     * lines one by one or completing an order (RF-03).
     */
    @Transactional
    public void empty() {
        clear(customers.requireCurrentCustomerId());
    }

    @Override
    public List<CartLineSnapshot> linesOf(Long customerId) {
        return items.findByCustomerId(customerId).stream()
                .map(item -> new CartLineSnapshot(item.getId().getProductId(), item.getQuantity()))
                .toList();
    }

    @Override
    @Transactional
    public void clear(Long customerId) {
        items.deleteByCustomerId(customerId);
    }

    /**
     * The cart as the contract describes it, composed from the three modules that hold its pieces.
     *
     * <p>{@link LinkedHashSet} for the identifiers: a cart cannot hold the same product twice, so
     * duplicates are not the concern — the insertion order is, because it is the order the lines were
     * read in and the response has to come back in that same order rather than in whichever one a
     * {@code Map} feels like.
     *
     * <p>A line whose product has vanished from the catalogue between two requests is skipped rather
     * than rendered without a name. It should not happen — {@code cart_item.product_id} is
     * {@code ON DELETE RESTRICT} (ADR-0013), so a product with lines in a cart cannot be deleted — and
     * that is precisely why the alternative is not worth building: there is no sensible thing to show,
     * and inventing one would be code exercised only by a broken database.
     */
    private CartResponse cartOf(Long customerId) {
        List<CartItem> lines = items.findByCustomerId(customerId);
        if (lines.isEmpty()) {
            return new CartResponse(List.of(), BigDecimal.ZERO.setScale(2));
        }

        Set<Long> productIds = lines.stream()
                .map(line -> line.getId().getProductId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, ProductSummary> products = catalog.summariesByIds(productIds);
        Map<Long, Integer> available = inventory.onlineQuantitiesByProductIds(productIds);

        List<CartLineResponse> cartLines = lines.stream()
                .filter(line -> products.containsKey(line.getId().getProductId()))
                .map(line -> lineOf(line, products, available))
                .toList();

        BigDecimal total = cartLines.stream()
                .map(CartLineResponse::lineTotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);

        return new CartResponse(cartLines, total);
    }

    /**
     * <strong>A discontinued product reports zero available, whatever its stock row says.</strong> The
     * line stays — the contract has no field to flag it and it is frozen since SINC-1 — but nothing that
     * cannot be bought is going to claim otherwise. Its stock row may well hold units: discontinuing a
     * product takes it out of the catalogue, it does not empty the warehouse (ADR-0013).
     */
    private static CartLineResponse lineOf(
            CartItem line, Map<Long, ProductSummary> products, Map<Long, Integer> available) {

        Long productId = line.getId().getProductId();
        ProductSummary product = products.get(productId);
        BigDecimal lineTotal = product.price().multiply(BigDecimal.valueOf(line.getQuantity()));

        return new CartLineResponse(
                productId,
                product.sku(),
                product.name(),
                product.price(),
                line.getQuantity(),
                lineTotal,
                product.active() ? available.getOrDefault(productId, 0) : 0);
    }
}
