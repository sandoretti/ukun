package dev.sandoretti.ukun.api.customers.infra;

import dev.sandoretti.ukun.api.customers.domain.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@code customer}, and through its cascade the profile's {@code address} and {@code card}.
 *
 * <p>The lookup by {@code account_id} is the hot query of the authenticated half of this service:
 * every request that has to answer "who is this?" goes through it, backed by
 * {@code ux_customer_account_id} ({@code docs/10-modelo-de-datos.md} §4). The {@code @EntityGraph}
 * fetches the address and the card in the same statement, which is what a profile read needs anyway
 * and what keeps defect #12 from reappearing here.
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @EntityGraph(attributePaths = {"address", "card"})
    Optional<Customer> findByAccountId(UUID accountId);

    /**
     * The same look-up on the same index, projecting only the identifier — what
     * {@code CustomersApi.requireCurrentCustomerId()} answers.
     *
     * <p>A method of its own rather than {@code findByAccountId(...).map(Customer::getId)}: the graph
     * above fetches the address and the card in the same statement, which is right for a profile read
     * and pure waste for a caller that wants a number. It is the first rung of the reading ladder of
     * {@code docs/03-analisis-y-patrones.md} §2 — a projection, when the projection is all that is
     * used.
     */
    @Query("select customer.id from Customer customer where customer.accountId = :accountId")
    Optional<Long> findIdByAccountId(@Param("accountId") UUID accountId);

    /**
     * The profile by its own identifier, with the address and the card in the same statement — what
     * {@code CustomersApi.snapshotOf(Long)} reads when checkout copies them into an order.
     *
     * <p>Named {@code findWithProfileById} rather than overriding {@code findById}: the inherited
     * method is used by anything that wants the row alone, and putting the graph on it would fetch two
     * associations for every caller that does not need them. The graph is not optional here — with
     * {@code open-in-view: false}, reading the address outside the transaction fails, and inside it
     * the two lazy loads would be defect #12 on the checkout path.
     */
    @EntityGraph(attributePaths = {"address", "card"})
    Optional<Customer> findWithProfileById(Long id);
}
