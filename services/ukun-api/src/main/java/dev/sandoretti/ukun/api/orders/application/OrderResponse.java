package dev.sandoretti.ukun.api.orders.application;

import dev.sandoretti.ukun.api.orders.domain.OrderStatus;
import dev.sandoretti.ukun.api.shared.web.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A whole order — the {@code OrderResponse} schema of the contract, and the body of all three
 * operations that return one: the confirmation, the history and the single read.
 *
 * <p><strong>The shipping address and the payment method are this order's own copies</strong>, not a
 * view of the customer's current profile (ADR-0011). It is the difference between a history and a
 * dashboard: the legacy shows every past order with today's address, because the order shares the row
 * (defect #19). Reading the same order twice, either side of a profile edit, has to give the same
 * answer — and there is now no row it could give a different one from.
 */
public record OrderResponse(
        Long id,
        Instant placedAt,
        OrderStatus status,
        @Money BigDecimal total,
        OrderAddressResponse shippingAddress,
        OrderPaymentResponse payment,
        List<OrderLineResponse> lines) {}
