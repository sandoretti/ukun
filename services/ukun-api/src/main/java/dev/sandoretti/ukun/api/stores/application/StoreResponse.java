package dev.sandoretti.ukun.api.stores.application;

/**
 * One entry of {@code GET /api/stores} — the {@code Store} schema of the contract (RF-02).
 *
 * <p>{@code isOnline} travels to the client because the SPA needs to tell the store that serves
 * orders (RF-04) from the ones you can walk into. There is exactly one with it set, guaranteed by
 * {@code ux_store_online} rather than by anything in this service.
 *
 * <p>No stock and no availability: what a store has in it is {@code GET /api/availability}, an
 * endpoint of the {@code inventory} module (ADR-0017 §3).
 */
public record StoreResponse(Long id, String name, boolean isOnline, String phone, AddressResponse address) {}
