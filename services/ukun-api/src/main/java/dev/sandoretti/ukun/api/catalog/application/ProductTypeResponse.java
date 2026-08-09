package dev.sandoretti.ukun.api.catalog.application;

/**
 * One entry of {@code GET /api/product-types} — the {@code ProductType} schema of the contract.
 *
 * <p>Two fields and no more. The back-office read of RF-13 adds {@code productCount},
 * {@code createdAt} and {@code updatedAt} under a schema of its own ({@code AdminProductType}, v0.7);
 * the filter of the public catalog needs a label and something to send back as {@code typeId}, and
 * counting products per type on every page load to fill a dropdown would be a join nobody asked for.
 *
 * <p>The Java name is {@code …Response} while the contract calls the schema {@code ProductType},
 * because that name is taken by the entity. The JSON is unaffected.
 */
public record ProductTypeResponse(Long id, String name) {}
