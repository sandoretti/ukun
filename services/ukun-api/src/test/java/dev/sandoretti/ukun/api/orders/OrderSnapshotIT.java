package dev.sandoretti.ukun.api.orders;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import dev.sandoretti.ukun.api.orders.application.OrderResponse;
import dev.sandoretti.ukun.api.orders.domain.PaymentBrand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Defect #19, the worst behavioural bug in the legacy system, written as a test.
 *
 * <p>In {@code ukun-clientes} an order keeps a foreign key to the customer's own {@code direccion} and
 * {@code tarjeta} rows, and {@code PerfilController.editar} cascades an {@code UPDATE} onto them. A
 * customer who moves house therefore rewrites the shipping address of every order they have ever
 * placed, and replacing an expired card rewrites how all of them were paid. The order history is not a
 * record of what happened — it is a view of the current profile wearing a history's clothes.
 *
 * <p>ADR-0011 fixes it structurally rather than by discipline: the values are columns of
 * {@code customer_order}, so there is no row left for another feature to update. These three tests are
 * what that claim means in practice, and each of them would fail against the legacy design even if
 * every line of the checkout were written correctly.
 */
class OrderSnapshotIT extends OrdersIntegrationTest {

    @Test
    @DisplayName("editing the profile does not change the address of a past order")
    void keepsTheShippingAddressOfAPastOrder() {
        OrderResponse placed = placeOrder(ADA, IN_STOCK, 1);

        moveAdaToBarcelona();

        OrderResponse reread = orderById(placed.id());

        assertSoftly(softly -> {
            softly.assertThat(reread.shippingAddress().street()).isEqualTo("Calle Mayor 1");
            softly.assertThat(reread.shippingAddress().city()).isEqualTo("Alcalá de Henares");
            softly.assertThat(reread.shippingAddress().province()).isEqualTo("Madrid");
            softly.assertThat(reread.shippingAddress().postalCode()).isEqualTo("28801");
            softly.assertThat(reread.shippingAddress()).isEqualTo(placed.shippingAddress());
        });
    }

    @Test
    @DisplayName("replacing the card does not change how a past order was paid")
    void keepsThePaymentOfAPastOrder() {
        OrderResponse placed = placeOrder(ADA, IN_STOCK, 1);

        moveAdaToBarcelona();

        OrderResponse reread = orderById(placed.id());

        assertSoftly(softly -> {
            softly.assertThat(reread.payment().last4()).isEqualTo("4242");
            softly.assertThat(reread.payment().brand()).isEqualTo(PaymentBrand.VISA);
            softly.assertThat(reread.payment().expMonth()).isEqualTo((short) 12);
            softly.assertThat(reread.payment().expYear()).isEqualTo((short) 2030);
        });
    }

    /**
     * The third field with the same problem, and the one RF-04 does not mention: the price of a line is
     * frozen in the legacy, the product's <em>name</em> is not. Renaming a product — which RF-10 allows
     * — would rewrite what every past order displays, and discontinuing it would leave the line
     * unreadable. {@code order_line.product_name} closes both ({@code docs/10-modelo-de-datos.md} §5).
     */
    @Test
    @DisplayName("renaming or repricing a product does not change a past order's lines")
    void keepsTheLinesOfAPastOrder() {
        OrderResponse placed = placeOrder(ADA, IN_STOCK, 2);

        jdbc.update("UPDATE product SET name = 'Sofá de piel', price = 999.99, is_active = false WHERE sku = ?", IN_STOCK);

        OrderResponse reread = orderById(placed.id());

        assertSoftly(softly -> {
            softly.assertThat(reread.lines().getFirst().productName()).isEqualTo("Sofá de cuero");
            softly.assertThat(reread.lines().getFirst().unitPrice()).isEqualByComparingTo("399.99");
            softly.assertThat(reread.lines().getFirst().totalPrice()).isEqualByComparingTo("799.98");
            softly.assertThat(reread.total()).isEqualByComparingTo("799.98");
        });
    }

    /**
     * The edit goes through {@code PUT /api/me/profile} rather than through SQL on purpose: the legacy
     * bug is not in its schema, it is in what its profile endpoint does to rows an order points at. A
     * test that updated the tables by hand would prove the columns are separate; this one proves the
     * endpoint cannot reach the order.
     */
    private void moveAdaToBarcelona() {
        restTestClient
                .put()
                .uri("/api/me/profile")
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                        """
                        {
                          "name": "Ada",
                          "lastName": "Lovelace",
                          "locale": "es",
                          "address": {
                            "street": "Avinguda Diagonal 200",
                            "city": "Barcelona",
                            "province": "Barcelona",
                            "postalCode": "08018"
                          },
                          "card": {
                            "last4": "1881",
                            "brand": "MASTERCARD",
                            "expMonth": 6,
                            "expYear": 2029
                          },
                          "favoriteStoreId": null
                        }
                        """)
                .exchange()
                .expectStatus()
                .isOk();
    }

    private OrderResponse orderById(Long id) {
        return restTestClient
                .get()
                .uri(ORDERS + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, bearer(customerToken(ADA)))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(OrderResponse.class)
                .returnResult()
                .getResponseBody();
    }
}
