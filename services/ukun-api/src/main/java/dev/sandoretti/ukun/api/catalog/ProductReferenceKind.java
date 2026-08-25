package dev.sandoretti.ukun.api.catalog;

/**
 * The three places a product can be referenced from, and the name each one answers by in the
 * {@code PRODUCT_IN_USE} body ({@code docs/12-error-contract.md}).
 *
 * <p>A closed enum rather than a free string, because the extensions of that {@code code} are a closed
 * list too: the frontend interpolates exactly these three, and a fourth invented in a module would
 * reach the client as a field nothing knows how to paint.
 */
public enum ProductReferenceKind {

    /** {@code order_line} — the one that can never be undone: order history is never rewritten. */
    ORDERS("inOrders"),

    /** {@code store_stock} — removable, by deleting the line in each store that carries the product. */
    STOCK("inStock"),

    /** {@code cart_item} — transient, and the only one that can clear itself as customers check out. */
    CARTS("inCarts");

    private final String extension;

    ProductReferenceKind(String extension) {
        this.extension = extension;
    }

    /** The key this count travels under in the {@code ProblemDetail}. */
    public String extension() {
        return extension;
    }
}
