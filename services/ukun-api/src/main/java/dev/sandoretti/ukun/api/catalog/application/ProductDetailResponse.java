package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.shared.web.Money;
import java.math.BigDecimal;

/**
 * The body of {@code GET /api/products/{id}} — the {@code ProductDetail} schema of the contract.
 *
 * <p>The contract composes it as {@code allOf: [ProductSummary, {description}]}. Java has no
 * inheritance between records, so the seven components of {@link ProductSummaryResponse} are repeated
 * here and {@code description} is appended. That is the honest translation: extracting a shared
 * interface would buy nothing at all — nobody consumes the two polymorphically — and the {@code allOf}
 * of the contract is a schema composition, not a runtime hierarchy.
 *
 * <p>{@code description} is the only thing the detail adds over a listing row. It is
 * {@code NOT NULL} in the table but the contract types it {@code [string, 'null']}, so the field is
 * declared nullable here rather than defended with a check the database already makes.
 *
 * <p><strong>What it deliberately does not carry.</strong> No {@code isActive}, no {@code createdAt},
 * no {@code updatedAt}: that is {@code AdminProduct} and it belongs to RF-10 in v0.7 — a public read
 * has no business announcing when a product was last edited. No availability either, for the same
 * reason {@link ProductSummaryResponse} carries none: stock lives in {@code inventory} and this module
 * cannot depend on it (ADR-0017 §3).
 */
public record ProductDetailResponse(
        Long id,
        String sku,
        String name,
        @Money BigDecimal price,
        Long typeId,
        String typeName,
        String imageUrl,
        String description) {}
