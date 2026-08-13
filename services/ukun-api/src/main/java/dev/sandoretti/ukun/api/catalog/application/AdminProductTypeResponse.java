package dev.sandoretti.ukun.api.catalog.application;

import java.time.Instant;

/**
 * One entry of {@code GET /api/admin/product-types} — the {@code AdminProductType} schema (RF-13).
 *
 * <p>The public {@link ProductTypeResponse} plus the three things only the back-office needs. They are
 * two reads of the same table and not the same resource twice: the public one fills a filter dropdown
 * on every catalogue page load, and joining a count onto it would be a query nobody asked for
 * (ADR-0020).
 *
 * <p><strong>{@code productCount} counts discontinued products too.</strong> That is not an oversight
 * of the count: it is what makes the number useful, because the foreign key that blocks the delete does
 * not care about {@code is_active} either (ADR-0013). A type whose products are all discontinued still
 * answers {@code 409} to a {@code DELETE}, and this field is what lets the screen say so before the
 * admin tries.
 */
public record AdminProductTypeResponse(Long id, String name, long productCount, Instant createdAt, Instant updatedAt) {}
