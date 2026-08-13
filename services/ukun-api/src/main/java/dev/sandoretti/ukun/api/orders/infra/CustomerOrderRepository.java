package dev.sandoretti.ukun.api.orders.infra;

import dev.sandoretti.ukun.api.orders.domain.CustomerOrder;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@code customer_order} with its lines — query Q3 of {@code docs/10-modelo-de-datos.md}: a customer's
 * history, most recent first.
 *
 * <p><strong>One paginated method with a composed {@link Specification}</strong>, not a family of
 * derived queries: three optional filters would otherwise be eight
 * {@code findByCustomerIdAndPlacedAtBetweenAndStatus…} combinations. Same decision, same reasons, as
 * {@code ProductRepository} ({@code docs/03-analisis-y-patrones.md} §2).
 *
 * <p><strong>The {@code @EntityGraph} is what keeps the history off defect #12.</strong> Every order
 * of the page renders its lines, so without it each row would fetch its collection in a query of its
 * own — the N+1 the legacy {@code /pedidos} listing has. Spring Data leaves the {@code count} query
 * alone, so the pair stays at exactly two statements however many orders come back.
 *
 * <p>Ordering is the caller's ({@code OrderQueryService}) and always ends in {@code id}: a page sorted
 * only by {@code placed_at} can show the same order twice or never, and two orders placed in the same
 * millisecond are not hypothetical here — the concurrency test creates them on purpose (defect #23).
 */
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long>, JpaSpecificationExecutor<CustomerOrder> {

    @Override
    @EntityGraph(attributePaths = "lines")
    Page<CustomerOrder> findAll(Specification<CustomerOrder> specification, Pageable pageable);

    /**
     * Order lines pointing at a product — the {@code inOrders} count of the {@code 409 PRODUCT_IN_USE}
     * of RF-10, and the one that never goes back to zero: order history is never rewritten (ADR-0012),
     * so a product that has been sold once can never be deleted and has to be discontinued instead.
     *
     * <p>Declared on this repository and not on one of {@code OrderLine}'s own, because there is none:
     * a line has no life outside its order ({@code docs/10-modelo-de-datos.md} §5) and giving it a
     * repository would suggest otherwise. What crosses to {@code catalog} through
     * {@code ProductReferenceCounter} is the number and nothing else — no order of any customer is
     * exposed by it.
     */
    @Query("select count(line) from OrderLine line where line.id.productId = :productId")
    long countOrderLinesByProductId(@Param("productId") Long productId);

    /**
     * One order of one customer, with its lines.
     *
     * <p><strong>The customer is part of the query, not of a check afterwards.</strong> The identifier
     * in the path identifies the order and does not authorise reading it
     * ({@code docs/08-identidad-y-seguridad.md} §4), so somebody else's order has to be indistinguishable
     * from one that does not exist — a {@code 404}, never a {@code 403}. Loading it and then comparing
     * owners in Java would give the same answer today and be one early return away from an existence
     * oracle over every order the shop has taken.
     */
    @EntityGraph(attributePaths = "lines")
    Optional<CustomerOrder> findByIdAndCustomerId(Long id, Long customerId);
}
