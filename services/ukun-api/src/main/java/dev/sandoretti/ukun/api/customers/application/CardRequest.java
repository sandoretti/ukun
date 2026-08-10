package dev.sandoretti.ukun.api.customers.application;

import dev.sandoretti.ukun.api.customers.domain.CardBrand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * The {@code Card} schema of the contract: a payment method that is <strong>already masked when it
 * arrives</strong>.
 *
 * <p>That is the part worth reading twice. The full number and the CVV are not fields this record
 * chose to leave out of storage — they never enter the service at all, in any request. The masking
 * happens in the browser, so there is no point in the system where a PAN could be logged by accident
 * (defect #15, {@code docs/08-identidad-y-seguridad.md} §5). The CVV is asked for again at each
 * purchase and never written down.
 *
 * <p>{@code expYear} is bounded by the {@code CHECK} of the baseline, {@code 2024..2100}: the lower
 * bound rejects a typo in a form, the upper one rejects a year that is not a card's.
 */
public record CardRequest(
        @Pattern(regexp = "^[0-9]{4}$", message = "must be four digits") String last4,
        @NotNull CardBrand brand,
        @Min(1) @Max(12) short expMonth,
        @Min(2024) @Max(2100) short expYear) {}
