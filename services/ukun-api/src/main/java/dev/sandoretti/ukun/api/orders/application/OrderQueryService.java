package dev.sandoretti.ukun.api.orders.application;

import dev.sandoretti.ukun.api.customers.CustomersApi;
import dev.sandoretti.ukun.api.orders.domain.CustomerOrder;
import dev.sandoretti.ukun.api.orders.infra.CustomerOrderRepository;
import dev.sandoretti.ukun.api.orders.infra.OrderSpecifications;
import dev.sandoretti.ukun.api.shared.error.ResourceNotFoundException;
import dev.sandoretti.ukun.api.shared.web.PageResponse;
import dev.sandoretti.ukun.api.shared.web.PaginationParams;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read side of orders (RF-04, query Q3): a customer's history and one order of it.
 *
 * <p><strong>Neither method takes a customer.</strong> Both resolve it from the token through
 * {@code CustomersApi}, and the identifier that does arrive — the order id in the path — identifies
 * the order without authorising anything ({@code docs/08-identidad-y-seguridad.md} §4). The ownership
 * is a {@code WHERE} clause, not a comparison after the fact; see
 * {@code CustomerOrderRepository.findByIdAndCustomerId}.
 *
 * <p>{@code @Transactional(readOnly = true)} at class level and a constructor that only assigns —
 * rule 6 of the skill {@code spring-api}, defect #6.
 */
@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    /**
     * Most recent first, as the contract says, <strong>with {@code id} as the last criterion</strong>.
     *
     * <p>That second half is defect #23 and it is not theoretical here: two orders can share a
     * {@code placed_at} to the microsecond — the concurrency test creates exactly that — and a page
     * ordered only by timestamp is then free to show one of them twice and the other never. Descending
     * on both, so the tie-break runs the same way as the sort a reader expects.
     *
     * <p>It matches {@code ix_customer_order_customer_placed (customer_id, placed_at DESC)}, which is
     * what lets Postgres drop the sort node entirely: equality first, order after
     * ({@code docs/10-modelo-de-datos.md} §4).
     */
    private static final Sort MOST_RECENT_FIRST =
            Sort.by(Sort.Direction.DESC, "placedAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final CustomerOrderRepository orders;
    private final OrderMapper mapper;
    private final CustomersApi customers;

    public OrderQueryService(CustomerOrderRepository orders, OrderMapper mapper, CustomersApi customers) {
        this.orders = orders;
        this.mapper = mapper;
        this.customers = customers;
    }

    /** {@code GET /api/orders}: the history, filtered and paginated. */
    public PageResponse<OrderResponse> list(OrderFilters filters, PaginationParams pagination) {
        Long customerId = customers.requireCurrentCustomerId();
        Page<CustomerOrder> page =
                orders.findAll(specificationFor(customerId, filters), pagination.toPageable(MOST_RECENT_FIRST));

        return PageResponse.of(page, mapper::toResponse);
    }

    /**
     * {@code GET /api/orders/{id}}: one order, with its lines and its snapshots.
     *
     * <p><strong>Somebody else's order is a {@code 404}, not a {@code 403}</strong>, and the contract
     * says so in as many words. A {@code 403} would confirm that the order exists, which turns this
     * endpoint into an oracle over how many orders the shop has taken and who has placed them. The
     * exception carries {@code resource} and {@code id} like any other missing resource, so from
     * outside the two cases are one.
     */
    public OrderResponse detail(Long id) {
        return orders
                .findByIdAndCustomerId(id, customers.requireCurrentCustomerId())
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    /**
     * The ownership predicate always, the three filters only when they arrived — the same composition
     * {@code ProductQueryService} does, and the reason there is one paginated query here instead of a
     * family of derived ones.
     */
    private static Specification<CustomerOrder> specificationFor(Long customerId, OrderFilters filters) {
        List<Specification<CustomerOrder>> predicates = new ArrayList<>();
        predicates.add(OrderSpecifications.belongsTo(customerId));

        if (filters.from() != null) {
            predicates.add(OrderSpecifications.placedFrom(filters.from()));
        }
        if (filters.to() != null) {
            predicates.add(OrderSpecifications.placedUpTo(filters.to()));
        }
        if (filters.status() != null) {
            predicates.add(OrderSpecifications.hasStatus(filters.status()));
        }

        return Specification.allOf(predicates);
    }
}
