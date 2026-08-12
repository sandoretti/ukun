package dev.sandoretti.ukun.api.orders.application;

import dev.sandoretti.ukun.api.cart.CartApi;
import dev.sandoretti.ukun.api.cart.CartLineSnapshot;
import dev.sandoretti.ukun.api.catalog.CatalogApi;
import dev.sandoretti.ukun.api.catalog.ProductSummary;
import dev.sandoretti.ukun.api.customers.CustomerSnapshot;
import dev.sandoretti.ukun.api.customers.CustomersApi;
import dev.sandoretti.ukun.api.inventory.InventoryApi;
import dev.sandoretti.ukun.api.inventory.StockDecrement;
import dev.sandoretti.ukun.api.inventory.StockDecrement.StockChange;
import dev.sandoretti.ukun.api.orders.domain.CheckoutAttempt;
import dev.sandoretti.ukun.api.orders.domain.CheckoutAttemptLine;
import dev.sandoretti.ukun.api.orders.domain.CustomerOrder;
import dev.sandoretti.ukun.api.orders.domain.IdempotencyRecord;
import dev.sandoretti.ukun.api.orders.domain.PaymentBrand;
import dev.sandoretti.ukun.api.orders.domain.PaymentSnapshot;
import dev.sandoretti.ukun.api.orders.domain.ShippingAddress;
import dev.sandoretti.ukun.api.orders.infra.CheckoutAttemptRepository;
import dev.sandoretti.ukun.api.orders.infra.CustomerOrderRepository;
import dev.sandoretti.ukun.api.orders.infra.IdempotencyRecordRepository;
import dev.sandoretti.ukun.api.shared.observability.BusinessEvents;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.event.Level;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Checkout (RF-04): opening an attempt, and turning it into an order exactly once.
 *
 * <p>This is the use case that touches the most aggregates in a single transaction — the cart, the
 * catalogue, the stock, the customer's profile and the order — which is why {@code orders} is the only
 * module with four declared dependencies (ADR-0017 §1). Everything it reaches for, it reaches through
 * another module's public API; it holds no entity that is not its own.
 *
 * <h2>The three legacy defects this class exists to not reproduce</h2>
 *
 * <ul>
 *   <li><strong>#2, overselling.</strong> The legacy reads the stock, subtracts in Java and writes it
 *       back, with nothing holding the row: two customers buying the last unit both get it. Here the
 *       decrement is {@code inventory}'s, under {@code SELECT … FOR UPDATE} ordered by stock line id
 *       (ADR-0008, ADR-0015).
 *   <li><strong>#19, the shared address.</strong> The legacy order points at the customer's live
 *       {@code direccion}/{@code tarjeta} rows, so editing a profile rewrites the history. Here the
 *       values are copied into the order's own columns at the moment of confirming (ADR-0011), which
 *       is what RF-04 asked for all along.
 *   <li><strong>#3 and the double order.</strong> The legacy places an order with {@code GET
 *       /pedidos/hacer} and has no idempotency at all, so a refresh buys everything twice. Here it is
 *       a {@code POST} whose key the server issued, and the second one gets the first one's answer
 *       (ADR-0016).
 * </ul>
 *
 * <p>{@code @Transactional(readOnly = true)} at class level with both entry points overriding it, and
 * a constructor that only assigns — rule 6 of the skill {@code spring-api}, defect #6.
 */
@Service
@Transactional(readOnly = true)
public class CheckoutService {

    private final CheckoutAttemptRepository attempts;
    private final IdempotencyRecordRepository idempotency;
    private final CustomerOrderRepository orders;
    private final OrderMapper mapper;
    private final CartApi cart;
    private final CatalogApi catalog;
    private final InventoryApi inventory;
    private final CustomersApi customers;
    private final ObjectMapper json;
    private final Clock clock;

    public CheckoutService(
            CheckoutAttemptRepository attempts,
            IdempotencyRecordRepository idempotency,
            CustomerOrderRepository orders,
            OrderMapper mapper,
            CartApi cart,
            CatalogApi catalog,
            InventoryApi inventory,
            CustomersApi customers,
            ObjectMapper json,
            Clock clock) {

        this.attempts = attempts;
        this.idempotency = idempotency;
        this.orders = orders;
        this.mapper = mapper;
        this.cart = cart;
        this.catalog = catalog;
        this.inventory = inventory;
        this.customers = customers;
        this.json = json;
        this.clock = clock;
    }

    /**
     * {@code POST /api/checkout/attempts}: freeze what the cart would cost, and issue the key that
     * confirming it will need.
     *
     * <p>Three statements' worth of reading, whatever the size of the cart: the lines, one batch call
     * to {@code CatalogApi} and one to {@code InventoryApi}. The batch-only signatures of both APIs are
     * what make the per-line loop of defect #12 unwriteable here.
     *
     * <p><strong>A line whose product is gone is dropped, not rendered.</strong> The same judgement
     * {@code CartService} makes, and for the same reason: {@code ON DELETE RESTRICT} means it should
     * not be possible, and inventing a display for it would be code exercised only by a broken
     * database. If dropping leaves nothing at all, the attempt is an empty cart and answers as one.
     */
    @Transactional
    public CheckoutAttemptResponse openAttempt() {
        Long customerId = customers.requireCurrentCustomerId();
        List<CartLineSnapshot> cartLines = cart.linesOf(customerId);

        if (cartLines.isEmpty()) {
            throw emptyCart(customerId);
        }

        Set<Long> productIds = cartLines.stream()
                .map(CartLineSnapshot::productId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, ProductSummary> products = catalog.summariesByIds(productIds);
        Map<Long, Integer> available = inventory.sellableOnlineQuantities(products);

        CheckoutAttempt attempt = new CheckoutAttempt(customerId, clock.instant());
        cartLines.stream()
                .filter(line -> products.containsKey(line.productId()))
                .forEach(line -> {
                    ProductSummary product = products.get(line.productId());
                    attempt.addLine(
                            line.productId(), product.sku(), product.name(), product.price(), line.quantity());
                });

        if (attempt.getLines().isEmpty()) {
            throw emptyCart(customerId);
        }

        CheckoutAttempt frozen = attempts.save(attempt.freeze());

        return new CheckoutAttemptResponse(
                frozen.getId(), linesOf(frozen, products, available), frozen.getTotal(), frozen.getExpiresAt());
    }

    /**
     * {@code POST /api/orders}: confirm an attempt, once.
     *
     * <p>The order of the checks is the contract's, and each one rules out a state the next would
     * misread:
     *
     * <ol>
     *   <li>The attempt exists and is fresh, or {@code 410 CHECKOUT_EXPIRED}. Everything downstream
     *       reads prices from it, so nothing else can be decided first.
     *   <li>The idempotency record decides: replay, in-flight or mismatch — three of the five rows of
     *       ADR-0016's table, all of them answered before anything else is looked at.
     *   <li>Only then, for a key never seen: the cart is not empty, or {@code 422 EMPTY_CART}.
     *   <li>Nothing has left the catalogue, then the stock is locked and taken, or
     *       {@code 409 INSUFFICIENT_STOCK}.
     * </ol>
     *
     * <p><strong>The replay is decided before the cart is looked at, and it has to be.</strong> A
     * successful checkout empties the cart (RF-03), so by the time the customer's browser retries — the
     * F5 this whole mechanism exists for — their cart is empty by construction. Checking it first
     * turned every legitimate retry into {@code 422 EMPTY_CART}, which is the opposite of the promise.
     *
     * <p><strong>The order is built from the attempt, not from the cart.</strong> The attempt is what
     * the customer was shown and agreed to pay, down to the price of each line; the cart is a draft
     * that may have moved since. RF-04's "the price is frozen at the moment of the order" and
     * ADR-0016's "the attempt freezes the unit price" are the same rule read from two ends, and this is
     * where they meet. A cart edited after the attempt was opened still changes the request hash, so a
     * <em>replay</em> against a different cart is caught — what is deliberately not done is silently
     * re-pricing a confirmation the customer already saw.
     *
     * <p><strong>One transaction, and it has to be.</strong> The lock taken by the decrement is only
     * worth something while it is held, so the order, the emptied cart and the completed idempotency
     * row all commit with it — or none of them do. That is also why the {@code IN_PROGRESS} row
     * disappears when the checkout fails: a customer rejected for lack of stock can confirm the same
     * attempt again when it is back, which a tombstone would forbid.
     */
    @Transactional
    public PlacedOrder placeOrder(UUID attemptId) {
        Long customerId = customers.requireCurrentCustomerId();
        Instant now = clock.instant();

        CheckoutAttempt attempt = attempts
                .findByIdAndCustomerId(attemptId, customerId)
                .orElseThrow(() -> CheckoutExpiredException.unknown(attemptId));

        if (attempt.hasExpired(now)) {
            throw CheckoutExpiredException.expired(attemptId, attempt.getExpiresAt());
        }

        String requestHash = hashOf(attempt);
        Optional<IdempotencyRecord> seen = idempotency.findByCustomerIdAndIdempotencyKey(customerId, attemptId);
        if (seen.isPresent()) {
            return replay(seen.get(), customerId, requestHash);
        }

        if (cart.linesOf(customerId).isEmpty()) {
            throw emptyCart(customerId);
        }

        IdempotencyRecord record = claim(attemptId, customerId, requestHash);

        CustomerOrder order = orderFrom(attempt, customers.snapshotOf(customerId), now);
        List<StockChange> movements = takeStock(attempt, customerId);

        CustomerOrder placed = orders.save(order.confirm());
        OrderResponse body = mapper.toResponse(placed);
        record.complete(placed.getId(), (short) 201, json.writeValueAsString(body));

        // Emitted here and not where the stock moved, because the registry keys this event by orderId
        // (docs/11 §3) and the order has only just been given one.
        movements.forEach(change -> BusinessEvents.emit(
                Level.DEBUG,
                "stock.decremented.checkout",
                Map.of(
                        "orderId", placed.getId(),
                        "productId", change.productId(),
                        "before", change.before(),
                        "after", change.after())));

        // Last, and not by accident: emptying the cart is a bulk statement that clears the persistence
        // context (`CartItemRepository.deleteByCustomerId`), so anything still pending afterwards would
        // be thrown away — the order's lines and the idempotency record among it.
        cart.clear(customerId);

        BusinessEvents.emit(
                Level.INFO,
                "order.created",
                Map.of(
                        "orderId", placed.getId(),
                        "customerId", customerId,
                        "total", placed.getTotal(),
                        "lineCount", placed.getLines().size(),
                        "idempotencyKey", attemptId));

        return PlacedOrder.created(body);
    }

    /**
     * The three answers a key that has been seen before can produce (ADR-0016).
     *
     * <p>The hash is compared <strong>before</strong> the status, and the order matters: a key reused
     * for a different cart is a {@code 422} whether the first request has finished or not. Asking
     * "is it still running?" first would answer {@code 409} to a caller whose real problem is that they
     * are confirming the wrong thing.
     */
    private PlacedOrder replay(IdempotencyRecord record, Long customerId, String requestHash) {
        if (!record.matches(requestHash)) {
            throw new IdempotencyMismatchException(record.getIdempotencyKey());
        }
        if (!record.isCompleted()) {
            throw new IdempotencyInProgressException(record.getIdempotencyKey());
        }

        BusinessEvents.emit(
                Level.INFO,
                "order.idempotent.replay",
                Map.of(
                        "customerId", customerId,
                        "idempotencyKey", record.getIdempotencyKey(),
                        "orderId", record.getOrderId()));

        return PlacedOrder.replayed(json.readValue(record.getResponseBody(), OrderResponse.class));
    }

    /**
     * Takes the key, or discovers that somebody else just did.
     *
     * <p><strong>The flush is the point of this method.</strong> Without it the insert would sit in the
     * persistence context until commit, by which time the order has been written and the stock taken —
     * and the unique violation would surface as a failed commit rather than as the {@code 409} the
     * contract declares. Flushing here makes the database arbitrate between two concurrent
     * confirmations at the only moment when neither has done any work yet.
     *
     * <p>{@code ux_idempotency_customer_key} is what arbitrates. Two transactions that both looked and
     * both saw nothing cannot both insert, and the one that loses is told so.
     */
    private IdempotencyRecord claim(UUID attemptId, Long customerId, String requestHash) {
        try {
            return idempotency.saveAndFlush(new IdempotencyRecord(attemptId, customerId, requestHash));
        } catch (DataIntegrityViolationException duplicate) {
            throw new IdempotencyInProgressException(attemptId);
        }
    }

    /** The aggregate, with the profile copied in rather than referenced (ADR-0011, defect #19). */
    private static CustomerOrder orderFrom(CheckoutAttempt attempt, CustomerSnapshot customer, Instant now) {
        CustomerOrder order = new CustomerOrder(
                attempt.getCustomerId(),
                now,
                new ShippingAddress(
                        customer.street(), customer.city(), customer.province(), customer.postalCode()),
                new PaymentSnapshot(
                        customer.cardLast4(),
                        PaymentBrand.of(customer.cardBrand()),
                        customer.cardExpMonth(),
                        customer.cardExpYear()));

        attempt.getLines()
                .forEach(line -> order.addLine(
                        line.getProductId(), line.getProductName(), line.getUnitPrice(), line.getQuantity()));

        return order;
    }

    /**
     * Everything that can stop an order from being served, turned into the error the contract declares.
     *
     * <p><strong>What can stop it is not decided here.</strong> A product that cannot be sold — because
     * it was discontinued between opening the checkout and confirming it, or deleted outright — is
     * refused by {@code InventoryApi} along with the one that simply ran out, and for the same reason:
     * availability is {@code inventory}'s answer to give (ADR-0013, issue #122). This module reads the
     * catalogue because the decrement needs it, not because it judges it. What is left here is the
     * translation into {@code 409 INSUFFICIENT_STOCK}, which is the half {@code inventory} cannot do —
     * it has never heard of a {@code sku} ({@code docs/12-error-contract.md}).
     *
     * <p>The {@code sku} the error carries comes from the attempt rather than from a fresh catalogue
     * read: it was frozen for exactly this, and the name a customer knows the product by should not
     * change between opening a checkout and being told it failed.
     *
     * @return what moved, for the caller to log once the order has an identifier to log it against.
     */
    private List<StockChange> takeStock(CheckoutAttempt attempt, Long customerId) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        attempt.getLines().forEach(line -> quantities.put(line.getProductId(), line.getQuantity()));

        Map<Long, ProductSummary> products = catalog.summariesByIds(quantities.keySet());

        return switch (inventory.decrementOnlineStock(quantities, products)) {
            case StockDecrement.Rejected rejected -> throw outOfStock(
                    customerId,
                    rejected.stockLineId(),
                    rejected.productId(),
                    skuOf(attempt, rejected.productId()),
                    rejected.available(),
                    rejected.requested());
            case StockDecrement.Applied applied -> applied.changes();
        };
    }

    /**
     * The lines of the response, composed from the three modules that hold their pieces — the same
     * shape {@code CartService} builds, and the reason neither goes through a mapper.
     *
     * <p>{@code available} is read now and reserves nothing; it is there so the customer sees the
     * problem before confirming rather than after (ADR-0016). A discontinued product reports zero
     * whatever its stock row holds, exactly as the cart reports it — and, as in the cart, that is the
     * answer {@code InventoryApi} gave rather than something this method works out (issue #122).
     */
    private static List<CheckoutAttemptLineResponse> linesOf(
            CheckoutAttempt attempt, Map<Long, ProductSummary> products, Map<Long, Integer> available) {

        return attempt.getLines().stream()
                .map(line -> {
                    // The catalogue's view of the product, which the caller already read to compute
                    // availability: the photo rides along with it, at no extra statement.
                    ProductSummary product = products.get(line.getProductId());

                    return new CheckoutAttemptLineResponse(
                            line.getSku(),
                            line.getProductName(),
                            product.imageUrl(),
                            line.getUnitPrice(),
                            line.getQuantity(),
                            available.getOrDefault(line.getProductId(), 0));
                })
                .toList();
    }

    /**
     * What the {@code request_hash} column holds: a fingerprint of what is being confirmed, so that a
     * key replayed against something else is {@code 422 IDEMPOTENCY_MISMATCH} instead of an ambiguous
     * answer.
     *
     * <p><strong>Over the attempt, and deliberately not over the live cart.</strong> ADR-0016 describes
     * the hash as covering "the attempt and the cart", and the attempt <em>is</em> the cart — frozen,
     * line by line, at the moment the checkout was opened. Reading the cart again here would make every
     * legitimate retry a mismatch, because a completed checkout empties the cart (RF-03): the F5 this
     * mechanism exists for would find an empty one and conclude the request had changed. What the ADR
     * is protecting against is a key used for a <em>different</em> purchase, and the frozen lines
     * capture that exactly.
     *
     * <p>The lines are already in a total order — by product id, {@code CheckoutAttempt.lines} — so the
     * fingerprint of one attempt is the same on every request, which is the whole requirement.
     *
     * <p>SHA-256 because the column is a 64-character hex string, not because anything here is a
     * secret: nobody is trying to forge a match.
     */
    private static String hashOf(CheckoutAttempt attempt) {
        StringBuilder fingerprint = new StringBuilder(attempt.getId().toString());
        attempt.getLines()
                .forEach(line -> fingerprint
                        .append('|')
                        .append(line.getProductId())
                        .append(':')
                        .append(line.getUnitPrice().toPlainString())
                        .append('x')
                        .append(line.getQuantity()));

        return sha256(fingerprint.toString());
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            // Every JVM ships SHA-256; the checked exception is an artefact of the 1.4-era API.
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private static String skuOf(CheckoutAttempt attempt, Long productId) {
        return attempt.getLines().stream()
                .filter(line -> line.getProductId().equals(productId))
                .map(CheckoutAttemptLine::getSku)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Product %d is not part of attempt %s".formatted(productId, attempt.getId())));
    }

    private static EmptyCartException emptyCart(Long customerId) {
        BusinessEvents.emit(Level.INFO, "order.rejected.empty_cart", Map.of("customerId", customerId));

        return new EmptyCartException();
    }

    private static InsufficientStockException outOfStock(
            Long customerId, Long stockLineId, Long productId, String sku, int available, int requested) {

        BusinessEvents.emit(
                Level.WARN,
                "order.rejected.stock",
                Map.of(
                        "customerId", customerId,
                        "productId", productId,
                        "requested", requested,
                        "available", available));

        return new InsufficientStockException(stockLineId, productId, sku, available, requested);
    }
}
