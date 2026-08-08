package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.shared.web.Money;
import java.math.BigDecimal;

/**
 * One row of {@code GET /api/products} — the {@code ProductSummary} schema of the contract.
 *
 * <p>A {@code record} mapped with MapStruct, never the entity serialised directly (ADR-0009): that
 * is what stops a column added tomorrow from silently appearing in a public response, which is how
 * the legacy leaked {@code contrasenna} in JSON (defect #4).
 *
 * <p><strong>It carries no availability.</strong> Stock lives in {@code inventory} and this module
 * cannot depend on it (ADR-0017 §3); the frontend calls {@code GET /api/availability} in parallel and
 * composes on screen. The intended side effect is that the catalog is cacheable with a long TTL and
 * availability is not.
 *
 * <p>The Java name is {@code …Response} while the contract calls the schema {@code ProductSummary}:
 * that name is reserved for the {@code record} of the module's public API, the one
 * {@code docs/14-modules.md} §3 already sketches for the {@code inventory} → {@code catalog} crossing
 * (issue #77). The JSON is unaffected.
 */
public record ProductSummaryResponse(
        Long id, String sku, String name, @Money BigDecimal price, Long typeId, String typeName, String imageUrl) {}
