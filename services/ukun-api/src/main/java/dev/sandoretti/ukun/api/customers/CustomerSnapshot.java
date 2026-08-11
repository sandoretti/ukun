package dev.sandoretti.ukun.api.customers;

/**
 * A customer's shipping address and payment method as they stand <strong>right now</strong>, for the
 * one caller that has to copy them: checkout.
 *
 * <p><strong>Why a flat record of eight values.</strong> RF-04 says the order copies the current
 * address and card "at the moment of confirming", and ADR-0011 makes that copy structural: the values
 * become columns of {@code customer_order}, with no row left anywhere that a later profile edit could
 * update. What crosses the module boundary therefore has to be values — the moment this carried a
 * {@code CustomerAddress}, {@code orders} would hold a reference to a row that keeps changing, which
 * is defect #19 with one indirection added.
 *
 * <p><strong>The brand travels as text.</strong> {@code customers} types it as
 * {@code customers.domain.CardBrand} and {@code orders} as {@code orders.domain.PaymentBrand}: two
 * enums with the same four values, constrained by the same two {@code CHECK}s in the database. That is
 * the cheaper of the two mistakes available here, and the same one {@code ProfileAddress} already
 * chose over a shared DTO — the alternative is publishing a type from a {@code domain} subpackage,
 * which {@code docs/14-modules.md} §2 does not allow, or moving it to this package where it would
 * become a type both modules pull in opposite directions.
 *
 * <p>No PAN and no CVV, because neither exists anywhere in this service ({@code docs/08} §5): what is
 * copied into an order is what is stored in the profile, which is already only the last four digits.
 */
public record CustomerSnapshot(
        String street,
        String city,
        String province,
        String postalCode,
        String cardLast4,
        String cardBrand,
        short cardExpMonth,
        short cardExpYear) {}
