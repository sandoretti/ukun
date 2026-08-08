/**
 * Stock per store: an employee's optimistic upsert and delete (RF-09) and the pessimistic decrement
 * during checkout (ADR-0008, ADR-0015). Owns: {@code store_stock}.
 *
 * <p>This is the side that carries the {@code catalog} dependency, the direction chosen to break the
 * {@code catalog}↔{@code inventory} cycle. Product data is always read in one batch call through
 * {@code CatalogApi}, never once per row (ADR-0017 §2 and §3, docs/14-modules.md §3).
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"catalog", "stores", "shared"}
)
package dev.sandoretti.ukun.api.inventory;
