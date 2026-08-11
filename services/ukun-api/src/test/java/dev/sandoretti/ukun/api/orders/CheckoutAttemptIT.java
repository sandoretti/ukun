package dev.sandoretti.ukun.api.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import dev.sandoretti.ukun.api.orders.application.CheckoutAttemptLineResponse;
import dev.sandoretti.ukun.api.orders.application.CheckoutAttemptResponse;
import dev.sandoretti.ukun.api.orders.domain.CheckoutAttempt;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * {@code POST /api/checkout/attempts} (ADR-0016): the quote the customer is shown before they commit
 * to anything.
 *
 * <p>Two assertions here are the reason the endpoint exists at all.
 * {@link #freezesThePriceAgainstALaterChange()} is the promise — the amount on the confirmation screen
 * is the amount that will be charged, whatever the catalogue does next — and
 * {@link #reportsAvailabilityWithoutReservingAnything()} is the limit of that promise: the attempt says
 * how many units there are and holds none of them, because ADR-0016 rules a TTL reservation out by
 * name.
 */
class CheckoutAttemptIT extends OrdersIntegrationTest {

    @Test
    @DisplayName("the attempt lists the cart at frozen prices, with its total and an expiry")
    void opensAnAttemptOverTheWholeCart() {
        addToCart(ADA, productIdOf(IN_STOCK), 2);
        addToCart(ADA, productIdOf(LAST_ONE), 1);

        CheckoutAttemptResponse attempt = openAttempt(ADA);

        assertSoftly(softly -> {
            softly.assertThat(attempt.attemptId()).isNotNull();
            softly.assertThat(attempt.lines())
                    .extracting(
                            CheckoutAttemptLineResponse::sku,
                            CheckoutAttemptLineResponse::productName,
                            CheckoutAttemptLineResponse::unitPrice,
                            CheckoutAttemptLineResponse::quantity,
                            CheckoutAttemptLineResponse::available)
                    .containsExactlyInAnyOrder(
                            tuple(IN_STOCK, "Sofá de cuero", new BigDecimal("399.99"), 2, 7),
                            tuple(LAST_ONE, "Litera infantil", new BigDecimal("399.99"), 1, 1));
            // 2 x 399.99 + 1 x 399.99, in BigDecimal and never in a Float (defect #5).
            softly.assertThat(attempt.total()).isEqualByComparingTo("1199.97");
            softly.assertThat(attempt.expiresAt()).isEqualTo(clock.instant().plus(CheckoutAttempt.LIFETIME));
        });
    }

    /**
     * RF-04 freezes the price "at the moment of the order", and ADR-0016 moves that moment forward to
     * when the checkout is opened. This is that rule: the catalogue is repriced after the attempt, and
     * the attempt does not notice.
     *
     * <p>The cart, by contrast, would show the new price immediately — it re-reads the catalogue on
     * every request by design. The difference between the two is the whole point of having an attempt.
     */
    @Test
    @DisplayName("a price change after the attempt does not change the attempt")
    void freezesThePriceAgainstALaterChange() {
        addToCart(ADA, productIdOf(IN_STOCK), 1);
        CheckoutAttemptResponse attempt = openAttempt(ADA);

        jdbc.update("UPDATE product SET price = 999.99 WHERE sku = ?", IN_STOCK);

        assertSoftly(softly -> {
            softly.assertThat(attempt.lines().getFirst().unitPrice()).isEqualByComparingTo("399.99");
            softly.assertThat(attempt.total()).isEqualByComparingTo("399.99");
        });
    }

    /**
     * Availability is information and nothing else: it reserves no units, and asking for more than
     * there are is not rejected here. The rejection, if it comes, is at confirmation time — which is
     * exactly what makes {@code 409 INSUFFICIENT_STOCK} a rare collision rather than the first the
     * customer hears of it.
     */
    @Test
    @DisplayName("availability is reported, not enforced, and nothing is reserved")
    void reportsAvailabilityWithoutReservingAnything() {
        addToCart(ADA, productIdOf(LAST_ONE), 5);

        CheckoutAttemptResponse attempt = openAttempt(ADA);

        assertSoftly(softly -> {
            softly.assertThat(attempt.lines().getFirst().quantity()).isEqualTo(5);
            softly.assertThat(attempt.lines().getFirst().available()).isEqualTo(1);
            softly.assertThat(onlineStockOf(LAST_ONE)).isEqualTo(1);
        });
    }

    /**
     * The confirmation screen shows the same photos the cart just showed (issue #120), so the line
     * carries the product's {@code imageUrl} — and {@code null} when the product has none (RF-10),
     * which the screen paints as an empty frame rather than a broken image.
     *
     * <p>Both cases in one test because what a screen needs is to tell them apart. It costs no extra
     * read: the photo rides on the {@code ProductSummary} already fetched to work out
     * {@code available}, which is what keeps this off the N+1 the batch-only {@code CatalogApi} exists
     * to prevent.
     */
    @Test
    @DisplayName("every line carries the product's photo, and null when the product has none")
    void carriesThePhotoOfEveryProduct() {
        addToCart(ADA, productIdOf(IN_STOCK), 1);
        addToCart(ADA, productIdOf(LAST_ONE), 1);

        assertThat(openAttempt(ADA).lines())
                .extracting(CheckoutAttemptLineResponse::sku, CheckoutAttemptLineResponse::imageUrl)
                .containsExactlyInAnyOrder(
                        tuple(IN_STOCK, "/media/products/sofa-cuero.jpg"), tuple(LAST_ONE, null));
    }

    /** RF-04: the shop sells from the online store and nowhere else, whatever Barcelona holds. */
    @Test
    @DisplayName("stock in another store reads as none available")
    void ignoresTheStockOfOtherStores() {
        addToCart(ADA, productIdOf(ONLY_IN_BARCELONA), 1);

        assertThat(openAttempt(ADA).lines().getFirst().available()).isZero();
    }

    /**
     * A product discontinued while it sat in the cart reports nothing available, whatever the warehouse
     * holds (ADR-0013). The number the customer sees here is the same one confirming will act on — see
     * {@code PlaceOrderIT}.
     */
    @Test
    @DisplayName("a product discontinued after being added reports zero available")
    void reportsZeroForADiscontinuedProduct() {
        long sofa = productIdOf(IN_STOCK);
        addToCart(ADA, sofa, 1);
        jdbc.update("UPDATE product SET is_active = false WHERE id = ?", sofa);

        assertThat(openAttempt(ADA).lines().getFirst().available()).isZero();
    }

    /**
     * RF-04, "do not carry over": the legacy turns an empty cart into an order with no lines and a
     * total of zero. There is nothing to check out, and saying so is a {@code 422} rather than an empty
     * {@code 201} that the confirmation screen would then have to make sense of.
     */
    @Test
    @DisplayName("an empty cart cannot be checked out: 422 EMPTY_CART")
    void refusesToOpenAnAttemptOverAnEmptyCart() {
        restTestClient
                .post()
                .uri(ATTEMPTS)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA)))
                .exchange()
                .expectStatus()
                .isEqualTo(422)
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("EMPTY_CART");
    }

    /** Two carts, one URL, no identifier in it: the attempt is the token holder's (docs/08 §4). */
    @Test
    @DisplayName("the attempt covers the caller's own cart and nobody else's")
    void opensTheAttemptOfTheTokenHolder() {
        addToCart(ADA, productIdOf(IN_STOCK), 1);
        addToCart(GRACE, productIdOf(LAST_ONE), 1);

        assertSoftly(softly -> {
            softly.assertThat(openAttempt(ADA).lines())
                    .extracting(CheckoutAttemptLineResponse::sku)
                    .containsExactly(IN_STOCK);
            softly.assertThat(openAttempt(GRACE).lines())
                    .extracting(CheckoutAttemptLineResponse::sku)
                    .containsExactly(LAST_ONE);
        });
    }

    /** An employee holds a valid token for this service and has no cart to check out (ADR-0006). */
    @Test
    @DisplayName("an employee is refused with 403 FORBIDDEN")
    void refusesAnEmployee() {
        restTestClient
                .post()
                .uri(ATTEMPTS)
                .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken()))
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("without a token it is a 401 UNAUTHENTICATED")
    void refusesAnAnonymousCaller() {
        restTestClient
                .post()
                .uri(ATTEMPTS)
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("UNAUTHENTICATED");
    }
}
