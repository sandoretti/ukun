package dev.sandoretti.ukun.api.orders.infra;

import dev.sandoretti.ukun.api.orders.domain.CustomerOrder;
import dev.sandoretti.ukun.api.orders.domain.OrderStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.data.jpa.domain.Specification;

/**
 * The composable predicates of the order history. The query service {@code and}s together the ones the
 * request carries, plus {@link #belongsTo(Long)}, which it always does — the alternative being the
 * eight derived queries {@code docs/03-analisis-y-patrones.md} §2 rules out.
 */
public final class OrderSpecifications {

    private static final String CUSTOMER_ID = "customerId";
    private static final String PLACED_AT = "placedAt";
    private static final String STATUS = "status";

    /**
     * The zone a {@code ?from=2026-08-11} refers to.
     *
     * <p><strong>UTC, and it is a decision rather than a default.</strong> The column is
     * {@code TIMESTAMPTZ}, so it stores instants and has no opinion; the contract has no timezone
     * parameter and no customer carries one. The two alternatives are worse: the JVM's default zone
     * makes the same request answer differently depending on the container's {@code TZ}, and inventing
     * a shop zone would be a configuration knob nobody has decided the value of. UTC is the one
     * boundary two deployments will agree on, and the ambiguity it leaves is at most a few hours at the
     * edge of a range that a customer chose by eye.
     */
    private static final ZoneOffset FILTER_ZONE = ZoneOffset.UTC;

    private OrderSpecifications() {}

    /**
     * Not an optional filter: a customer only ever reads their own orders. It is the {@code WHERE}
     * clause the identity rule of {@code docs/08-identidad-y-seguridad.md} §4 turns into SQL, and it is
     * applied in the query rather than checked over the results so no page can ever be built from
     * somebody else's rows.
     */
    public static Specification<CustomerOrder> belongsTo(Long customerId) {
        return (root, query, builder) -> builder.equal(root.get(CUSTOMER_ID), customerId);
    }

    /** Inclusive: orders placed at any point during {@code from}, in {@link #FILTER_ZONE}, are in. */
    public static Specification<CustomerOrder> placedFrom(LocalDate from) {
        Instant start = from.atStartOfDay(FILTER_ZONE).toInstant();

        return (root, query, builder) -> builder.greaterThanOrEqualTo(root.get(PLACED_AT), start);
    }

    /**
     * Inclusive too, which is why it compares against the start of the <em>next</em> day and not
     * against {@code to} itself: {@code placed_at <= 2026-08-11T00:00:00Z} would drop every order of
     * that day but the ones placed exactly at midnight — the off-by-a-day that makes a customer
     * conclude their last order vanished.
     */
    public static Specification<CustomerOrder> placedUpTo(LocalDate to) {
        Instant endExclusive = to.plusDays(1).atStartOfDay(FILTER_ZONE).toInstant();

        return (root, query, builder) -> builder.lessThan(root.get(PLACED_AT), endExclusive);
    }

    public static Specification<CustomerOrder> hasStatus(OrderStatus status) {
        return (root, query, builder) -> builder.equal(root.get(STATUS), status);
    }
}
