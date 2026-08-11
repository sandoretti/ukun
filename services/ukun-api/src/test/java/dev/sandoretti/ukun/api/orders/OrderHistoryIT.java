package dev.sandoretti.ukun.api.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import dev.sandoretti.ukun.api.orders.application.OrderResponse;
import dev.sandoretti.ukun.api.shared.web.PageResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;

/**
 * {@code GET /api/orders} and {@code GET /api/orders/{id}} (RF-04, query Q3): the history and one order
 * of it.
 *
 * <p>Two of these tests are about failures the legacy has and a listing endpoint invites.
 * {@link #readsAPageOfOrdersInAConstantNumberOfStatements()} is the N+1 of defect #12 — the legacy
 * {@code /pedidos} fetches each order's lines in a query of its own — and
 * {@link #ordersPlacedAtTheSameInstantPaginateStably()} is defect #23, which is not hypothetical here:
 * the suite's clock stands still, so two orders really do share a {@code placed_at} to the microsecond.
 */
class OrderHistoryIT extends OrdersIntegrationTest {

    private static final ParameterizedTypeReference<PageResponse<OrderResponse>> ORDER_PAGE =
            new ParameterizedTypeReference<>() {};

    @Test
    @DisplayName("the history is the caller's own orders, most recent first")
    void listsTheCallersOrdersMostRecentFirst() {
        OrderResponse first = placeOrder(ADA, IN_STOCK, 1);
        clock.advance(Duration.ofMinutes(5));
        OrderResponse second = placeOrder(ADA, IN_STOCK, 2);
        placeOrder(GRACE, IN_STOCK, 1);

        PageResponse<OrderResponse> page = history(ADA, "");

        assertSoftly(softly -> {
            softly.assertThat(page.items()).extracting(OrderResponse::id).containsExactly(second.id(), first.id());
            softly.assertThat(page.totalElements()).isEqualTo(2);
            softly.assertThat(page.items().getFirst().lines()).isNotEmpty();
        });
    }

    /**
     * Defect #23. Both orders are placed at the same instant — the clock does not move between them —
     * so a page ordered only by {@code placed_at} could show one of them twice and the other never.
     * Every {@code Sort} in this API ends in {@code id} for exactly this, and here the two pages of one
     * element each have to be two different orders.
     */
    @Test
    @DisplayName("two orders placed at the same instant paginate without repeating or losing one")
    void ordersPlacedAtTheSameInstantPaginateStably() {
        OrderResponse first = placeOrder(ADA, IN_STOCK, 1);
        OrderResponse second = placeOrder(ADA, LAST_ONE, 1);

        List<Long> paged = List.of(
                history(ADA, "?page=0&size=1").items().getFirst().id(),
                history(ADA, "?page=1&size=1").items().getFirst().id());

        assertSoftly(softly -> {
            softly.assertThat(first.placedAt()).isEqualTo(second.placedAt());
            softly.assertThat(paged).containsExactlyInAnyOrder(first.id(), second.id());
        });
    }

    /** Both bounds are inclusive, so an order placed today is inside {@code from=today&to=today}. */
    @Test
    @DisplayName("the date filters are inclusive at both ends")
    void filtersByAnInclusiveDateRange() {
        OrderResponse placed = placeOrder(ADA, IN_STOCK, 1);
        LocalDate today = placed.placedAt().atZone(ZoneOffset.UTC).toLocalDate();

        assertSoftly(softly -> {
            softly.assertThat(history(ADA, "?from=%s&to=%s".formatted(today, today)).items())
                    .hasSize(1);
            softly.assertThat(history(ADA, "?from=%s".formatted(today.plusDays(1))).items())
                    .isEmpty();
            softly.assertThat(history(ADA, "?to=%s".formatted(today.minusDays(1))).items())
                    .isEmpty();
        });
    }

    @Test
    @DisplayName("filtering by status returns the confirmed orders")
    void filtersByStatus() {
        placeOrder(ADA, IN_STOCK, 1);

        assertThat(history(ADA, "?status=CONFIRMED").items()).hasSize(1);
    }

    /**
     * The anti-N+1 assertion of this slice. Every order of the page renders its lines, so without the
     * {@code @EntityGraph} each row would fetch its collection in a query of its own — which is what
     * the legacy {@code /pedidos} listing does (defect #12). Three orders must cost exactly what one
     * does.
     *
     * <p>The number is asserted as well as the equality, because "constant" alone would still hold if
     * somebody made it constant and expensive.
     */
    @Test
    @DisplayName("reading the history costs the same two statements with one order and with three")
    void readsAPageOfOrdersInAConstantNumberOfStatements() {
        placeOrder(ADA, IN_STOCK, 1);
        long withOneOrder = statementsToRead(1);

        placeOrder(ADA, IN_STOCK, 1);
        placeOrder(ADA, LAST_ONE, 1);
        long withThreeOrders = statementsToRead(3);

        assertSoftly(softly -> {
            // The customer behind the token, and the page of orders with their lines. There is no
            // third: Spring Data skips the COUNT when a page starts at zero and comes back short of
            // its size, because it already knows the total.
            softly.assertThat(withOneOrder).isEqualTo(2);
            softly.assertThat(withThreeOrders).isEqualTo(withOneOrder);
        });
    }

    @Test
    @DisplayName("one order is read by the id the Location pointed at")
    void readsOneOrder() {
        OrderResponse placed = placeOrder(ADA, IN_STOCK, 2);

        OrderResponse read = restTestClient
                .get()
                .uri(ORDERS + "/" + placed.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA)))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(OrderResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(read).isEqualTo(placed);
    }

    /**
     * The identifier in the path identifies the order and does not authorise reading it
     * ({@code docs/08-identidad-y-seguridad.md} §4). It answers {@code 404} and not {@code 403}, because
     * a {@code 403} would confirm the order exists — an existence oracle over every order the shop has
     * taken.
     */
    @Test
    @DisplayName("another customer's order is a 404, not a 403")
    void hidesSomebodyElsesOrder() {
        OrderResponse ada = placeOrder(ADA, IN_STOCK, 1);

        restTestClient
                .get()
                .uri(ORDERS + "/" + ada.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(GRACE)))
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("RESOURCE_NOT_FOUND")
                .jsonPath("$.resource")
                .isEqualTo("Order");
    }

    @Test
    @DisplayName("an order that does not exist is a 404 too")
    void answers404ForAnOrderThatDoesNotExist() {
        restTestClient
                .get()
                .uri(ORDERS + "/999999")
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA)))
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    @DisplayName("a customer with no orders gets an empty page, not a 404")
    void answersAnEmptyPageForACustomerWithNoOrders() {
        PageResponse<OrderResponse> page = history(ADA, "");

        assertSoftly(softly -> {
            softly.assertThat(page.items()).isEmpty();
            softly.assertThat(page.totalElements()).isZero();
        });
    }

    @Test
    @DisplayName("a size beyond the contract's bound is a 400 VALIDATION_FAILED")
    void rejectsAnUnboundedPageSize() {
        restTestClient
                .get()
                .uri(ORDERS + "?size=1000")
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA)))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("VALIDATION_FAILED");
    }

    private long statementsToRead(int expectedOrders) {
        Statistics statistics = freshStatistics();

        assertThat(history(ADA, "").items()).hasSize(expectedOrders);

        return statistics.getPrepareStatementCount();
    }

    private PageResponse<OrderResponse> history(java.util.UUID accountId, String query) {
        return restTestClient
                .get()
                .uri(ORDERS + query)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(accountId)))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(ORDER_PAGE)
                .returnResult()
                .getResponseBody();
    }
}
