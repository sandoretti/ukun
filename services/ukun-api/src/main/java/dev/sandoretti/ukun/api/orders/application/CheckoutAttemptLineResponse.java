package dev.sandoretti.ukun.api.orders.application;

import dev.sandoretti.ukun.api.shared.web.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/**
 * One line of a checkout attempt — the {@code CheckoutAttemptLine} schema of the contract.
 *
 * <p><strong>{@code unitPrice} is the price frozen when the attempt was opened</strong>, unlike the
 * cart's, which is whatever the product costs at the moment of reading. That difference is the whole
 * purpose of the attempt: the amount on the confirmation screen is the amount that will be charged,
 * and it stops being a promise the moment somebody re-reads the catalogue instead of this row.
 *
 * <p><strong>{@code available} is information and nothing more.</strong> It reserves no stock —
 * ADR-0016 rules a TTL reservation out by name — so it may be gone by the time the customer confirms.
 * What it buys is that {@code 409 INSUFFICIENT_STOCK} becomes the rare case of a real collision rather
 * than the first the customer hears about a product being out of stock.
 *
 * <p>It carries the {@code sku} and not the {@code productId}: the contract says so, and it is the
 * right call — this is a receipt-shaped object, and a SKU is what the shop and the customer both call
 * the thing. The identifier appears where a machine needs it, in the {@code INSUFFICIENT_STOCK}
 * extensions.
 *
 * <p><strong>{@code imageUrl} is read from the catalogue and not stored</strong> — the opposite of
 * {@code unitPrice} two paragraphs up, and the difference is the point. What the attempt writes down
 * is the amount the customer is agreeing to pay; a photo is not part of that agreement, so
 * {@code checkout_attempt_line} gains no column and this schema no changeset. It costs no statement
 * either: the {@code ProductSummary} it comes from is already being read to work out
 * {@code available}.
 *
 * <p>{@code OrderLine} has no photo at all, and that is the same decision seen from the other side: an
 * order outlives its product, and the snapshot ADR-0011 calls for covers what the order owes — name,
 * price, quantity — not a picture that can be replaced under a two-year-old receipt.
 *
 * <p>{@code null} for a product with no photo (RF-10), which the screen renders as an empty frame
 * rather than a broken image. The nullability is stated in an annotation because springdoc cannot
 * infer it from a reference type, and {@code OpenApiDriftIT} compares the published document against
 * the contract — the same reason the three {@code imageUrl} of {@code catalog} and the cart's carry
 * one.
 */
public record CheckoutAttemptLineResponse(
        String sku,
        String productName,
        @Schema(nullable = true, format = "uri-reference") String imageUrl,
        @Money BigDecimal unitPrice,
        int quantity,
        int available) {}
