package dev.sandoretti.ukun.api.catalog.application;

import dev.sandoretti.ukun.api.shared.web.Money;
import java.math.BigDecimal;

/**
 * One suggestion of {@code GET /api/products/suggest} — the {@code ProductSuggestion} schema of the
 * contract.
 *
 * <p>Five fields, which is what a dropdown row draws: a thumbnail, a name, a price, and the
 * {@code id} the browser navigates to. No {@code typeId}/{@code typeName} and no {@code description},
 * and that is what keeps the query free of the join {@link ProductSummaryResponse} needs — the
 * association is never touched, so there is no N+1 to prevent here in the first place.
 *
 * <p>{@code sku} is in the contract and stays: it is the business identifier (ADR-0015) and it is what
 * an employee types into the box, so the suggestion has to be able to show it back.
 */
public record ProductSuggestionResponse(
        Long id, String sku, String name, @Money BigDecimal price, String imageUrl) {}
