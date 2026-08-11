package dev.sandoretti.ukun.api.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import dev.sandoretti.ukun.api.orders.application.CheckoutAttemptResponse;
import dev.sandoretti.ukun.api.orders.application.OrderLineResponse;
import dev.sandoretti.ukun.api.orders.application.OrderResponse;
import dev.sandoretti.ukun.api.orders.domain.CheckoutAttempt;
import dev.sandoretti.ukun.api.orders.domain.OrderStatus;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * {@code POST /api/orders} (RF-04, ADR-0016): confirming a checkout, and the five ways a repeated
 * confirmation is answered.
 *
 * <p>The suite is built around what the legacy gets wrong. It has no idempotency at all and places its
 * orders with {@code GET /pedidos/hacer} (defect #3), so a refresh buys everything twice; it has no
 * stock check worth the name (defect #2); and it happily writes an order with no lines and a total of
 * zero (RF-04, "do not carry over"). Each of those has a test here that fails if the behaviour comes
 * back.
 */
class PlaceOrderIT extends OrdersIntegrationTest {

    @Test
    @DisplayName("confirming an attempt creates the order, empties the cart and takes the stock")
    void placesTheOrder() {
        addToCart(ADA, productIdOf(IN_STOCK), 2);
        UUID attemptId = openAttempt(ADA).attemptId();

        OrderResponse order = confirm(ADA, attemptId);

        assertSoftly(softly -> {
            softly.assertThat(order.id()).isNotNull();
            softly.assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
            softly.assertThat(order.placedAt()).isEqualTo(clock.instant());
            softly.assertThat(order.total()).isEqualByComparingTo("799.98");
            softly.assertThat(order.lines())
                    .singleElement()
                    .extracting(
                            OrderLineResponse::productName,
                            OrderLineResponse::unitPrice,
                            OrderLineResponse::quantity,
                            OrderLineResponse::totalPrice)
                    .containsExactly("Sofá de cuero", new java.math.BigDecimal("399.99"), 2, new java.math.BigDecimal("799.98"));
            // RF-03: the cart is emptied when the order completes, and the stock is down by two.
            softly.assertThat(cartLinesOf(ADA)).isZero();
            softly.assertThat(onlineStockOf(IN_STOCK)).isEqualTo(5);
        });
    }

    /** The {@code Location} of a fresh order points at the endpoint the confirmation screen reads. */
    @Test
    @DisplayName("a created order answers 201 with a Location that resolves")
    void answersWithALocationThatResolves() {
        addToCart(ADA, productIdOf(IN_STOCK), 1);
        UUID attemptId = openAttempt(ADA).attemptId();

        String location = restTestClient
                .post()
                .uri(ORDERS)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA)))
                .header(IDEMPOTENCY_KEY, attemptId.toString())
                .exchange()
                .expectStatus()
                .isCreated()
                .expectHeader()
                .exists(HttpHeaders.LOCATION)
                .returnResult(OrderResponse.class)
                .getResponseHeaders()
                .getFirst(HttpHeaders.LOCATION);

        restTestClient
                .get()
                .uri(location)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA)))
                .exchange()
                .expectStatus()
                .isOk();
    }

    /**
     * The double click, the retry after a timeout, the F5 on the confirmation screen. All three send
     * the same key twice, and all three have to get the same order back rather than a second one —
     * which is the whole of ADR-0016 and the bug the legacy has by construction.
     */
    @Test
    @DisplayName("confirming twice with the same key answers 200 with the same order, and creates only one")
    void replaysInsteadOfCreatingASecondOrder() {
        addToCart(ADA, productIdOf(IN_STOCK), 2);
        UUID attemptId = openAttempt(ADA).attemptId();

        OrderResponse first = confirm(ADA, attemptId);
        OrderResponse replay = restTestClient
                .post()
                .uri(ORDERS)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA)))
                .header(IDEMPOTENCY_KEY, attemptId.toString())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(OrderResponse.class)
                .returnResult()
                .getResponseBody();

        assertSoftly(softly -> {
            softly.assertThat(replay).isEqualTo(first);
            softly.assertThat(ordersOf(ADA)).isEqualTo(1);
            // The effect is not repeated either: the stock moved once, not twice.
            softly.assertThat(onlineStockOf(IN_STOCK)).isEqualTo(5);
        });
    }

    /**
     * A key used against a different cart is a {@code 422} rather than either of the two lies available:
     * serving the stored order would confirm something the caller did not ask for, and recomputing would
     * break the promise that one key means one order.
     *
     * <p>The row is written straight into {@code idempotency_key} with a hash that cannot match,
     * because the hash is computed server-side over the attempt and the cart — a test that could
     * produce a matching one would be reimplementing the thing under test.
     */
    @Test
    @DisplayName("a key already used against a different cart is a 422 IDEMPOTENCY_MISMATCH")
    void refusesAKeyUsedForADifferentCart() {
        addToCart(ADA, productIdOf(IN_STOCK), 1);
        UUID attemptId = openAttempt(ADA).attemptId();

        jdbc.update(
                """
                INSERT INTO idempotency_key (key, customer_id, status, request_hash)
                VALUES (?, (SELECT id FROM customer WHERE account_id = ?), 'COMPLETED', ?)
                """,
                attemptId,
                ADA,
                "0".repeat(64));

        expectCode(ADA, attemptId, 422, "IDEMPOTENCY_MISMATCH");
        assertThat(ordersOf(ADA)).isZero();
    }

    /**
     * Thirty minutes is the lifetime ADR-0016 fixes. Past it the frozen price is no longer a promise
     * the shop is making, so confirming is refused and the frontend reopens the checkout — which is why
     * the code is {@code 410} and not a generic {@code 404}.
     */
    @Test
    @DisplayName("an attempt past its expiry is a 410 CHECKOUT_EXPIRED, and says when it expired")
    void refusesAnExpiredAttempt() {
        addToCart(ADA, productIdOf(IN_STOCK), 1);
        CheckoutAttemptResponse attempt = openAttempt(ADA);

        clock.advance(CheckoutAttempt.LIFETIME.plus(Duration.ofMinutes(1)));

        restTestClient
                .post()
                .uri(ORDERS)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA)))
                .header(IDEMPOTENCY_KEY, attempt.attemptId().toString())
                .exchange()
                .expectStatus()
                .isEqualTo(410)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("CHECKOUT_EXPIRED")
                .jsonPath("$.attemptId")
                .isEqualTo(attempt.attemptId().toString())
                .jsonPath("$.expiredAt")
                .isEqualTo(attempt.expiresAt().toString());

        assertThat(ordersOf(ADA)).isZero();
    }

    /** An attempt nobody ever opened answers exactly like a stale one, minus the {@code expiredAt}. */
    @Test
    @DisplayName("an attempt that never existed is also a 410, with no expiredAt")
    void refusesAnUnknownAttempt() {
        restTestClient
                .post()
                .uri(ORDERS)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA)))
                .header(IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                .exchange()
                .expectStatus()
                .isEqualTo(410)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("CHECKOUT_EXPIRED")
                .jsonPath("$.expiredAt")
                .doesNotExist();
    }

    /**
     * Somebody else's attempt is indistinguishable from one that never existed. Two different answers
     * would let a caller probe for other people's checkouts, which is the identity rule of
     * {@code docs/08-identidad-y-seguridad.md} §4 applied to a header instead of a path.
     */
    @Test
    @DisplayName("another customer's attempt cannot be confirmed: 410, the same as an unknown one")
    void refusesSomebodyElsesAttempt() {
        addToCart(ADA, productIdOf(IN_STOCK), 1);
        UUID attemptId = openAttempt(ADA).attemptId();
        addToCart(GRACE, productIdOf(LAST_ONE), 1);

        expectCode(GRACE, attemptId, 410, "CHECKOUT_EXPIRED");
        assertThat(ordersOf(GRACE)).isZero();
    }

    /**
     * RF-04: if any line lacks stock the whole order is rejected — no partial orders, no backorders. The
     * sofa has seven units and would be servable; the litera has one and three are asked for, so nothing
     * moves at all.
     */
    @Test
    @DisplayName("one line short rejects the whole order, and nothing is decremented")
    void rejectsTheWholeOrderWhenOneLineIsShort() {
        addToCart(ADA, productIdOf(IN_STOCK), 2);
        addToCart(ADA, productIdOf(LAST_ONE), 3);
        UUID attemptId = openAttempt(ADA).attemptId();

        restTestClient
                .post()
                .uri(ORDERS)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA)))
                .header(IDEMPOTENCY_KEY, attemptId.toString())
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INSUFFICIENT_STOCK")
                .jsonPath("$.sku")
                .isEqualTo(LAST_ONE)
                .jsonPath("$.available")
                .isEqualTo(1)
                .jsonPath("$.requested")
                .isEqualTo(3)
                .jsonPath("$.stockLineId")
                .exists();

        assertSoftly(softly -> {
            softly.assertThat(ordersOf(ADA)).isZero();
            softly.assertThat(onlineStockOf(IN_STOCK)).isEqualTo(7);
            softly.assertThat(onlineStockOf(LAST_ONE)).isEqualTo(1);
            // The cart survives a rejected checkout: there is nothing to buy yet.
            softly.assertThat(cartLinesOf(ADA)).isEqualTo(2);
        });
    }

    /**
     * A product with no stock line online is rejected as having none, and the extension says so by
     * <em>omitting</em> {@code stockLineId} — there is no line to point at. That difference exists
     * nowhere else in the API: RF-01 requires the public reads to make "no row" and "a row at zero"
     * indistinguishable.
     */
    @Test
    @DisplayName("a product with no stock line online is rejected with no stockLineId")
    void rejectsAProductWithNoStockLine() {
        addToCart(ADA, productIdOf(ONLY_IN_BARCELONA), 1);
        UUID attemptId = openAttempt(ADA).attemptId();

        restTestClient
                .post()
                .uri(ORDERS)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA)))
                .header(IDEMPOTENCY_KEY, attemptId.toString())
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("INSUFFICIENT_STOCK")
                .jsonPath("$.available")
                .isEqualTo(0)
                .jsonPath("$.stockLineId")
                .doesNotExist();
    }

    /**
     * Discontinuing a product takes it out of the catalogue without emptying the warehouse (ADR-0013),
     * so the units really are there — and selling them anyway would make "discontinued" mean nothing to
     * the one operation where it matters. The customer already saw {@code available: 0} on the attempt.
     */
    @Test
    @DisplayName("a product discontinued between the attempt and the confirmation cannot be bought")
    void refusesAProductDiscontinuedAfterTheAttempt() {
        long sofa = productIdOf(IN_STOCK);
        addToCart(ADA, sofa, 1);
        UUID attemptId = openAttempt(ADA).attemptId();

        jdbc.update("UPDATE product SET is_active = false WHERE id = ?", sofa);

        expectCode(ADA, attemptId, 409, "INSUFFICIENT_STOCK");

        assertSoftly(softly -> {
            softly.assertThat(ordersOf(ADA)).isZero();
            softly.assertThat(onlineStockOf(IN_STOCK)).isEqualTo(7);
        });
    }

    /**
     * The cart can be emptied in another tab between opening the checkout and confirming it. RF-04 says
     * an order with no lines is a bug, so the answer is the same {@code 422} the attempt itself gives.
     */
    @Test
    @DisplayName("confirming after the cart was emptied is a 422 EMPTY_CART")
    void refusesToConfirmAnEmptyCart() {
        addToCart(ADA, productIdOf(IN_STOCK), 1);
        UUID attemptId = openAttempt(ADA).attemptId();

        restTestClient
                .delete()
                .uri("/api/cart")
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA)))
                .exchange()
                .expectStatus()
                .isNoContent();

        expectCode(ADA, attemptId, 422, "EMPTY_CART");
        assertThat(ordersOf(ADA)).isZero();
    }

    /**
     * A key that is not a UUID was never issued by this server, so it is rejected by Spring's own
     * conversion before any of the checkout runs — and it still comes back as a {@code ProblemDetail}
     * with a {@code code}, which is what {@code GlobalExceptionHandler} extending
     * {@code ResponseEntityExceptionHandler} buys.
     */
    @Test
    @DisplayName("a malformed Idempotency-Key is a 400")
    void refusesAMalformedKey() {
        restTestClient
                .post()
                .uri(ORDERS)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA)))
                .header(IDEMPOTENCY_KEY, "not-a-uuid")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .exists();
    }

    @Test
    @DisplayName("an employee cannot place an order: 403 FORBIDDEN")
    void refusesAnEmployee() {
        restTestClient
                .post()
                .uri(ORDERS)
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken()))
                .header(IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    private void expectCode(UUID accountId, UUID attemptId, int status, String code) {
        restTestClient
                .post()
                .uri(ORDERS)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(accountId)))
                .header(IDEMPOTENCY_KEY, attemptId.toString())
                .exchange()
                .expectStatus()
                .isEqualTo(status)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(code);
    }

    private int ordersOf(UUID accountId) {
        return jdbc.queryForObject(
                """
                SELECT count(*) FROM customer_order
                 WHERE customer_id = (SELECT id FROM customer WHERE account_id = ?)
                """,
                Integer.class,
                accountId);
    }

    private int cartLinesOf(UUID accountId) {
        return jdbc.queryForObject(
                """
                SELECT count(*) FROM cart_item
                 WHERE customer_id = (SELECT id FROM customer WHERE account_id = ?)
                """,
                Integer.class,
                accountId);
    }
}
