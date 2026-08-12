/**
 * Stock per store: an employee's optimistic upsert and delete (RF-09) and the pessimistic decrement
 * during checkout (ADR-0008, ADR-0015). Owns: {@code store_stock}.
 *
 * <p>This is the side that carries the {@code catalog} dependency, the direction chosen to break the
 * {@code catalog}↔{@code inventory} cycle. Product data is always read in one batch call through
 * {@code CatalogApi}, never once per row (ADR-0017 §2 and §3, docs/14-modules.md §3).
 *
 * <p><strong>{@code identity} joined the list in v0.7, with the management half of RF-09.</strong> The
 * {@code {storeId}} of {@code /api/stores/{storeId}/stock} identifies the resource and authorises
 * nothing ({@code docs/08-identidad-y-seguridad.md} §4), so this module has to ask who is making the
 * request before it can answer whether the store is theirs. It is the same crossing {@code customers}
 * already declares, and it closes no cycle: {@code identity} depends on nobody.
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"catalog", "stores", "identity", "shared"}
)
package dev.sandoretti.ukun.api.inventory;
