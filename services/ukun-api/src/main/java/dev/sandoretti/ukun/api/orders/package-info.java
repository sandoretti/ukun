/**
 * Orders and checkout (RF-04): the {@code Order} aggregate with its immutable shipping and payment
 * snapshots (ADR-0011) and the server-issued checkout attempt that makes {@code POST /api/orders}
 * idempotent (ADR-0016). Owns: {@code customer_order}, {@code order_line}, {@code idempotency_key}.
 *
 * <p>The only module with four declared dependencies, and it has to be: checkout is the use case that
 * touches the most aggregates in a single transaction (ADR-0017 §1).
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"cart", "inventory", "customers", "catalog"}
)
package dev.sandoretti.ukun.api.orders;
