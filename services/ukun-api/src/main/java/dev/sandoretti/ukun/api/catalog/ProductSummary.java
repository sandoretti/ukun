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
 * public catalog, so a caller has to tell one from a product that never existed: {@code cart} refuses
 * to add the first, and {@code inventory} reports nothing available for it, whatever its stock row
 * holds (issue #122). Asking a second question to find that out would be a query per line, which is
 * the shape this API exists to prevent.
 *
 * <p><strong>Why {@code imageUrl} travels.</strong> Because a caller shows the product to a person,
 * and a line without a photo is identified by its name and its SKU alone. {@code cart} is the case
 * that settled it (issue #120): the alternative from the browser is one {@code GET /api/products/
 * {id}} per line, which is the N+1 this whole API exists to make impossible — and for a thumbnail.
 * It costs no extra statement here: the photo is {@code product.image_key}, a column of the row
 * already being read, not an association.
 *
 * <p>The URL and never the key ({@code docs/08-identidad-y-seguridad.md} §9): the key is an internal
 * detail of {@code FileStoragePort}, and composing a URL out of an identifier is not something a
 * caller — in another module or in the browser — is allowed to know how to do.
 *
 * <p>The name is the one {@code ProductSummaryResponse} was already deferring to: that class is the
 * row of {@code GET /api/products}, and its javadoc reserves {@code ProductSummary} for exactly this
 * type. The two are still not interchangeable — the response carries {@code typeName}, which no
 * module outside {@code catalog} has ever needed.
 */
public record ProductSummary(
        Long id, String sku, String name, String imageUrl, BigDecimal price, boolean active) {}
