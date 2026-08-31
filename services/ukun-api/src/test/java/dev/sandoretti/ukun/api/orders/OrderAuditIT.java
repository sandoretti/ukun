package dev.sandoretti.ukun.api.orders;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.api.audit.AuditRows;
import dev.sandoretti.ukun.api.orders.application.OrderResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * What a confirmed order leaves in {@code audit_log} (RF-04, ADR-0014).
 *
 * <p><strong>This is the suite that shows why the trail is not Hibernate Envers.</strong> Placing an
 * order writes an order, its lines and a decrement of {@code store_stock} — and leaves exactly
 * <em>one</em> audit row, {@code ORDER_CREATED}. Envers would version each touched entity and report
 * the stock movement as an edit to {@code store_stock}, indistinguishable from an employee correcting
 * the shelf count; here the two are different actions by different people, which is the distinction
 * ADR-0014 says an audit trail exists to make. {@code stock.decremented.checkout} is in the registry of
 * {@code docs/11} §3 at {@code DEBUG} and without a tick, precisely so.
 */
class OrderAuditIT extends OrdersIntegrationTest {

    @BeforeEach
    void clearTheTrail() {
        AuditRows.clear(jdbc);
    }

    @Test
    @DisplayName("a confirmed order records ORDER_CREATED against the order, by the customer")
    void auditsAPlacedOrder() {
        OrderResponse order = placeOrder(ADA, IN_STOCK, 2);

        AuditRows.Row row = AuditRows.only(jdbc);

        assertThat(row.action()).isEqualTo("ORDER_CREATED");
        assertThat(row.resourceType()).isEqualTo("customer_order");
        assertThat(row.resourceId()).isEqualTo(String.valueOf(order.id()));
        assertThat(row.actorAccountId()).isEqualTo(ADA);
        assertThat(row.actorRole()).isEqualTo("CUSTOMER");
        assertThat(AuditRows.detailsOf(row)).containsKeys("total", "lineCount", "idempotencyKey");
    }

    /**
     * The order carries a frozen copy of the shipping address and the payment (ADR-0011), so this is the
     * second place after the profile where PII and PCI are within reach of {@code details}. Neither may
     * be in it ({@code docs/11} §4): the row says an order was placed and for how much, and the order
     * itself is where its address lives.
     */
    @Test
    @DisplayName("the audited order carries no address and no card")
    void auditsWithoutTheSnapshot() {
        OrderResponse order = placeOrder(ADA, IN_STOCK, 1);

        assertThat(AuditRows.only(jdbc).details())
                .doesNotContain(order.shippingAddress().street())
                .doesNotContain(order.shippingAddress().postalCode())
                .doesNotContain(order.payment().last4());
    }

    /**
     * Taking stock during a checkout is not an employee editing stock, and the trail must not read as if
     * it were. One order, one row — not one plus a movement per line.
     */
    @Test
    @DisplayName("the stock the checkout consumes is not audited as a stock edit")
    void doesNotAuditTheStockMovement() {
        placeOrder(ADA, IN_STOCK, 2);

        assertThat(AuditRows.all(jdbc)).extracting(AuditRows.Row::action).containsExactly("ORDER_CREATED");
    }

    /**
     * The replay of ADR-0016 answers from the stored response without doing the work again, so it must
     * not audit again either — a trail that grew a row per retry would report one order as several.
     */
    @Test
    @DisplayName("confirming the same attempt twice records one order, not two")
    void doesNotAuditAnIdempotentReplay() {
        addToCart(ADA, productIdOf(IN_STOCK), 1);
        UUID attempt = openAttempt(ADA).attemptId();

        confirm(ADA, attempt);
        replay(ADA, attempt);

        assertThat(AuditRows.count(jdbc)).isEqualTo(1);
    }

    /** The second send of the same key: a {@code 200} off the stored answer, not a {@code 201}. */
    private void replay(UUID accountId, UUID attemptId) {
        restTestClient
                .post()
                .uri(ORDERS)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(accountId)))
                .header(IDEMPOTENCY_KEY, attemptId.toString())
                .exchange()
                .expectStatus()
                .isOk();
    }
}
