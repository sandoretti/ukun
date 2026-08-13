package dev.sandoretti.ukun.api.catalog;

/**
 * How many rows of a module's own table point at a product. Implemented by every module that holds a
 * {@code product_id}, and used by exactly one caller: the delete of RF-10, which has to say
 * <em>where</em> the references are before refusing ({@code 409 PRODUCT_IN_USE}, ADR-0013).
 *
 * <h2>Why the interface lives here and the implementations do not</h2>
 *
 * The rows to count are in {@code order_line}, {@code store_stock} and {@code cart_item} — three tables
 * this module does not own and must not read ({@code docs/14-modules.md} §2). But {@code catalog}
 * cannot depend on those modules either: {@code inventory}, {@code cart} and {@code orders} already
 * depend on <em>it</em>, and the edge back would close the very cycle ADR-0017 §3 broke by choosing a
 * direction.
 *
 * <p>So the dependency is inverted rather than added. {@code catalog} publishes what it needs to know,
 * each owner implements it over its own table, and Spring hands the use case the implementations. Every
 * arrow still points at {@code catalog}, the declared graph of ADR-0017 §1 is untouched, and
 * {@code ApplicationModules.verify()} sees nothing new — which is the test that this is a real solution
 * and not a rephrasing of the problem.
 *
 * <p>The alternative that was weighed and rejected: putting the counting inside {@code catalog} as
 * native queries over the three tables, the way {@code inventory} borrows {@code employee} today. That
 * borrowing is documented as a temporary exception for a table whose module has no code yet; these
 * three have owners, and reading their tables would make {@code catalog} the second place that knows
 * what a cart line is.
 */
public interface ProductReferenceCounter {

    /** Which of the three counts this implementation answers for. Every kind has exactly one. */
    ProductReferenceKind kind();

    /**
     * Rows of this module's table pointing at {@code productId}. Zero when there are none — never an
     * exception, because "nothing references it" is the answer that lets the delete go ahead.
     *
     * <p>It counts rather than lists on purpose: the client is told how many references block the
     * delete, and nothing about them crosses a module boundary.
     */
    long countReferencesTo(Long productId);
}
