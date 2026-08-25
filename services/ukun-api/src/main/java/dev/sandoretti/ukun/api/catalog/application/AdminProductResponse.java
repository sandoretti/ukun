package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.shared.web.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * The {@code AdminProduct} schema (RF-10): what the back-office sees of a product, and the body of
 * every write of this CRUD.
 *
 * <p>The contract composes it as {@code allOf: [ProductDetail, {isActive, createdAt, updatedAt}]}, and
 * records do not inherit, so the eight components of {@link ProductDetailResponse} are repeated here
 * with the three management fields appended — the same honest translation that record already makes of
 * {@code ProductSummary}.
 *
 * <p>What separates it from the public detail is not extra data for its own sake: {@code isActive} is
 * the field that makes a discontinued product findable at all, and without it the back-office could
 * not reactivate what the public catalogue refuses to show (ADR-0013). The two are different reads of
 * one table — one anonymous and cacheable, one neither (ADR-0020).
 *
 * <p>It carries no {@code version} and no availability. The first because editing a product is
 * last-write-wins by decision ({@code docs/10-modelo-de-datos.md} §2), the second because stock lives
 * in {@code inventory} and this module does not depend on it (ADR-0017 §3).
 */
public record AdminProductResponse(
        Long id,
        String sku,
        String name,
        @Money BigDecimal price,
        Long typeId,
        String typeName,
        @Schema(nullable = true, format = "uri-reference") String imageUrl,
        @Schema(nullable = true) String description,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt) {}
