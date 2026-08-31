/**
 * Product catalogue: listing and product detail (RF-01), admin CRUD and product image (RF-10).
 * Owns: {@code product}, {@code product_type}.
 *
 * <p>Depends on {@code storage} for the image file only. It must <strong>not</strong> depend on
 * {@code inventory}: availability travels in its own endpoint precisely to break that cycle
 * (ADR-0017 §3).
 */
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"storage", "shared", "audit"}
)
package dev.sandoretti.ukun.api.catalog;
