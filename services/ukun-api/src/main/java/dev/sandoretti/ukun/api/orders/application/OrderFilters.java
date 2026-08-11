package dev.sandoretti.ukun.api.orders.application;

import dev.sandoretti.ukun.api.orders.domain.OrderStatus;
import java.time.LocalDate;

/**
 * The three optional filters of {@code GET /api/orders} (RF-04, query Q3). All three are nullable: a
 * filter that was not sent is not applied.
 *
 * <p>It carries no customer, and that is not an omission. Whose history this is comes from the token
 * and is resolved server-side ({@code docs/08-identidad-y-seguridad.md} §4); a {@code customerId} that
 * could be bound from the query string is the legacy antipattern this whole module was written to
 * avoid.
 *
 * <p>{@code from} and {@code to} are dates, not instants — the contract declares
 * {@code format: date} — and both are <strong>inclusive</strong>. Turning a day into the half-open
 * range of instants a {@code TIMESTAMPTZ} column needs is {@code OrderSpecifications}' job, along with
 * the decision about which zone that day belongs to.
 *
 * <p>Like {@code ProductFilters}, it lives next to the use case rather than in {@code web/}: it is the
 * input of the query, not a detail of HTTP, and the controller only binds it.
 */
public record OrderFilters(LocalDate from, LocalDate to, OrderStatus status) {}
