/**
 * Shopping cart of a customer (RF-03). Owns: {@code cart_item}.
 *
 * <p>Reads current name and price from {@code catalog} and availability from {@code inventory}, both
 * in batch, and resolves its owner through {@code customers} (ADR-0017 §1 and §2).
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"catalog", "inventory", "customers", "shared"}
)
package dev.sandoretti.ukun.api.cart;
