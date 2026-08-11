package dev.sandoretti.ukun.api.cart;

/**
 * What a cart line looks like to another module: a product and how many of it.
 *
 * <p>It carries no price and no name on purpose. Checkout freezes both at the moment the order is
 * placed, read from {@code catalog} in its own transaction (RF-04, ADR-0011) — taking them from here
 * would mean the order copied whatever the cart happened to have read earlier, which is the same class
 * of bug as the shared address of defect #19, one indirection removed.
 */
public record CartLineSnapshot(Long productId, int quantity) {}
