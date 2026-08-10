package dev.sandoretti.ukun.api.customers.infra;

import dev.sandoretti.ukun.api.customers.domain.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
