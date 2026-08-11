package dev.sandoretti.ukun.api.catalog;

import java.math.BigDecimal;

/**
 * What another module gets to know about a product: the four fields {@code docs/14-modules.md} §3
 * sketches, plus whether it is still on sale.
 *
 * <p>A {@code record} and never the {@code Product} entity, which is the golden rule of §2 of that
 * document and an ArchUnit rule besides. The difference is not ceremony: an entity handed across a
 * module boundary brings its lazy associations with it, and the caller — outside the transaction that
 * loaded it, with {@code open-in-view: false} — would only find out at runtime.
 *
 * <p><strong>Why {@code active} travels.</strong> A discontinued product (ADR-0013) is out of the
 * public catalog, so {@code cart} has to tell one from a product that never existed: the first cannot
 * be added to a cart, and a line already holding it reports nothing available. Asking a second
 * question to find that out would be a query per line, which is the shape this API exists to prevent.
 *
 * <p>The name is the one {@code ProductSummaryResponse} was already deferring to: that class is the
 * row of {@code GET /api/products}, and its javadoc reserves {@code ProductSummary} for exactly this
 * type. The two are not interchangeable — the response carries {@code typeName} and {@code imageUrl},
 * which are of interest to a screen and to nobody else.
 */
public record ProductSummary(Long id, String sku, String name, BigDecimal price, boolean active) {}
