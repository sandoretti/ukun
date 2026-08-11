package dev.sandoretti.ukun.api.orders.application;

import dev.sandoretti.ukun.api.orders.domain.PaymentBrand;

/**
 * The {@code Card} schema of the contract, as an order carries it: how this order was paid.
 *
 * <p>Masked, and there is nothing behind the mask to unmask: no PAN and no CVV exist anywhere in this
 * service ({@code docs/08-identidad-y-seguridad.md} §5, defect #15). The four values here are the four
 * columns of {@code customer_order}, frozen when the order was placed so that replacing an expired
 * card does not rewrite what a past order says it was paid with (ADR-0011, defect #19).
 */
public record OrderPaymentResponse(String last4, PaymentBrand brand, short expMonth, short expYear) {}
