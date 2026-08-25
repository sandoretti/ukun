package dev.sandoretti.ukun.api.customers.application;

import dev.sandoretti.ukun.api.customers.domain.Customer;
import dev.sandoretti.ukun.api.customers.infra.CustomerRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Step 3 of the signup saga: the profile, its address and its card, written to {@code ukun_app} in
 * <strong>one</strong> local transaction.
 *
 * <p>"One" is the design, not an implementation detail. The saga has exactly one transactional step
 * because that is what lets the rest of it be a sequence of idempotent calls rather than a distributed
 * transaction ({@code docs/08-identidad-y-seguridad.md} §3): either all three rows land or none does,
 * and a partial profile is never a state the compensation has to reason about. The cascade from
 * {@link Customer} is what expresses it as a single {@code save} instead of three ordered ones.
 *
 * <p>It is a bean of its own, handed to the orchestrator as its
 * {@link dev.sandoretti.ukun.api.identity.LocalProfileWriter}, for a reason the saga makes concrete:
 * the orchestrator must <em>not</em> run in a transaction, because it makes two HTTP calls to another
 * service, and holding a database connection open across a network round trip is how a pool gets
 * exhausted by one slow dependency. Keeping the transactional part in a separate bean is also what
 * makes the proxy apply — a private method calling itself would silently get no transaction at all.
 */
@Component
class CustomerRegistrar {

    private final CustomerRepository customers;
    private final SignupMapper mapper;

    CustomerRegistrar(CustomerRepository customers, SignupMapper mapper) {
        this.customers = customers;
        this.mapper = mapper;
    }

    /**
     * @param accountId the account ukun-auth reserved, stored opaquely and without a foreign key
     *     because it lives in another database ({@code docs/08} §1)
     * @return the id of the new profile, for the {@code signup.completed} event
     */
    @Transactional
    long register(UUID accountId, SignupRequest request) {
        // customer.email is the read projection of docs/08 §2, not the credential — that one stays in
        // ukun-auth, and this copy is refreshed from the token's claim on the owner's next request.
        Customer customer = mapper.toCustomer(request, accountId);
        customer.setAddress(mapper.toAddress(request.address()));
        // Already masked when it arrived: there is no PAN and no CVV to leave out, because neither
        // ever entered the service (docs/08 §5).
        customer.setCard(mapper.toCard(request.card()));

        return customers.save(customer).getId();
    }
}
