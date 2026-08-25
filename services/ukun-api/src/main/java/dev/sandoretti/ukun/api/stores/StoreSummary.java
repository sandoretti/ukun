package dev.sandoretti.ukun.api.stores;

/**
 * What another module gets to know about a store: which one it is, and what to call it.
 *
 * <p>A {@code record} and never the {@code Store} entity, which is the golden rule of
 * {@code docs/14-modules.md} §2 and an ArchUnit rule besides. The difference is not ceremony: an
 * entity handed across a module boundary brings its lazy associations with it — here the address —
 * and the caller, outside the transaction that loaded it and with {@code open-in-view: false}, would
 * only find out at runtime.
 *
 * <p>Two fields, and the address is not one of them. A store's address is what
 * {@code GET /api/stores} exists to show; a caller in another module holds a {@code store_id} and
 * needs to name it to a person, which is a different question with a much smaller answer. The day
 * somebody genuinely needs the address outside {@code stores}, it is a field added here with a
 * reason written next to it, not a second method returning the entity.
 *
 * <p>The name is the one {@code StoreResponse} was already leaving free: that class is the row of
 * {@code GET /api/stores}, with its {@code isOnline} and its nested address, and the two are
 * deliberately not interchangeable.
 */
public record StoreSummary(Long id, String name) {}
