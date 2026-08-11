package dev.sandoretti.ukun.api.customers.application;

import dev.sandoretti.ukun.api.customers.CustomerSnapshot;
import dev.sandoretti.ukun.api.customers.CustomersApi;
import dev.sandoretti.ukun.api.customers.domain.Customer;
import dev.sandoretti.ukun.api.customers.domain.ProfileLocale;
import dev.sandoretti.ukun.api.customers.infra.CustomerRepository;
import dev.sandoretti.ukun.api.identity.CurrentAccount;
import dev.sandoretti.ukun.api.identity.IdentityApi;
import dev.sandoretti.ukun.api.identity.Role;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code GET /api/me}: who is holding this token, and in what language to talk to them. Since the cart,
 * also the module's {@link CustomersApi} — the same crossing from {@code account_id} to the row this
 * module owns, answered for the modules that may not talk to {@code identity} themselves.
 *
 * <p><strong>Why this lives in {@code customers} and not in {@code identity}</strong>, even though it
 * serves employees too. The answer carries {@code locale}, which is a column of {@code customer}, and
 * {@code identity} may not read it: {@code customers} already depends on {@code identity}
 * ({@code customers/package-info.java}), so the reverse edge would close a cycle and
 * {@code ApplicationModules.verify()} would say so. The module that owns the data answers the question,
 * which is the same reason {@code inventory} owns {@code /api/availability} (ADR-0017 §3).
 *
 * <p>It is one query, on {@code ux_customer_account_id} — the hottest index in the system, because this
 * is the endpoint the SPA calls after every login ({@code docs/01-arquitectura.md} §2).
 */
@Service
@Transactional(readOnly = true)
public class CurrentUserService implements CustomersApi {

    /**
     * What an employee gets. {@code employee} has no {@code locale} column and that is deliberate
     * (ADR-0019, and the baseline says so where the table is created): the back-office is one language
     * per installation, and a preference nobody can edit is a column that only ever holds its default.
     */
    private static final ProfileLocale DEFAULT_LOCALE = ProfileLocale.ES;

    private final CustomerRepository customers;
    private final ProfileMapper mapper;
    private final IdentityApi identity;

    public CurrentUserService(CustomerRepository customers, ProfileMapper mapper, IdentityApi identity) {
        this.customers = customers;
        this.mapper = mapper;
        this.identity = identity;
    }

    public CurrentUserResponse current(CurrentAccount account) {
        ProfileLocale locale = customers.findByAccountId(account.accountId())
                .map(Customer::getLocale)
                .map(mapper::toLocale)
                .orElse(DEFAULT_LOCALE);

        return new CurrentUserResponse(account.accountId(), account.email(), rolesOf(account), locale);
    }

    /**
     * {@link CustomersApi#requireCurrentCustomerId()}: the crossing from the account in the token to the
     * row this module owns, for the modules that may not talk to {@code identity}.
     *
     * <p>It lives in this class for the same reason {@code GET /api/me} does — this is where the
     * {@code account_id} look-up already happens — and reads the token through {@link IdentityApi} so
     * that no caller ever touches a claim name itself.
     */
    @Override
    public Long requireCurrentCustomerId() {
        CurrentAccount account = identity.requireCurrentAccount();

        return customers
                .findIdByAccountId(account.accountId())
                .orElseThrow(() -> new MissingProfileException(account.accountId()));
    }

    /**
     * {@link CustomersApi#snapshotOf(Long)}: the address and card checkout freezes into an order.
     *
     * <p>{@link IllegalStateException} and not a {@code 404}: the caller resolved this identifier from
     * the token one method call ago, so a row that is not there means this service lost a profile
     * between two statements of the same request. A stack trace is what gets that fixed; a {@code 404}
     * would tell the customer their own order does not exist.
     */
    @Override
    public CustomerSnapshot snapshotOf(Long customerId) {
        return customers
                .findWithProfileById(customerId)
                .map(mapper::toSnapshot)
                .orElseThrow(() -> new IllegalStateException("Customer %d has no profile".formatted(customerId)));
    }

    /**
     * Sorted, because {@link CurrentAccount} holds a {@code Set} and the contract publishes an array: an
     * order that changes between two calls of the same session is a diff in a snapshot test and a
     * cache key that never hits, for no reason anybody would enjoy finding.
     */
    private static List<Role> rolesOf(CurrentAccount account) {
        return account.roles().stream().sorted(Comparator.naturalOrder()).toList();
    }
}
