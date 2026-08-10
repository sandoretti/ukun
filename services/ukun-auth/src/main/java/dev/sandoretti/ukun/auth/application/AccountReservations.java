package dev.sandoretti.ukun.auth.application;

import dev.sandoretti.ukun.auth.domain.Account;
import dev.sandoretti.ukun.auth.domain.RegistrationRequest;
import dev.sandoretti.ukun.auth.domain.Role;
import dev.sandoretti.ukun.auth.infra.AccountRepository;
import dev.sandoretti.ukun.auth.infra.RegistrationRequestRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single write of a reservation: the account, its roles and the idempotency key, in one
 * transaction that either lands whole or not at all.
 *
 * <p><strong>Why this is a collaborator and not a method of {@link AccountsService}.</strong> The
 * insert can lose two different races, and recovering from either one means <em>reading</em> after the
 * write failed. A failed statement marks its transaction rollback-only, so that read cannot happen
 * inside the same transaction — and a private method of {@code AccountsService} calling itself would
 * not get a new one either, because self-invocation bypasses the proxy that would start it. Splitting
 * the transactional write into its own bean is what makes {@code AccountsService.reserve} able to run
 * outside a transaction and open a fresh one per attempt.
 *
 * <p>It translates nothing. The {@code DataIntegrityViolationException} travels up as it is, because
 * only the caller knows which of the two constraints it was worth re-reading for.
 */
@Component
class AccountReservations {

    private final AccountRepository accounts;
    private final RegistrationRequestRepository registrations;
    private final PasswordEncoder passwordEncoder;

    AccountReservations(
            AccountRepository accounts,
            RegistrationRequestRepository registrations,
            PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.registrations = registrations;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Writes the reservation and forces it to reach the database before returning.
     *
     * <p>The {@code flush} is the point of the method. Without it the insert would go out when the
     * transaction commits, <em>after</em> this frame has returned, and the constraint violation would
     * surface somewhere the caller cannot tell the two races apart.
     *
     * @return the id of the account created, which this method chooses rather than the database: it is
     *     the {@code sub} claim of every token that account will ever get.
     */
    @Transactional
    UUID insert(UUID idempotencyKey, String email, String rawPassword, Set<Role> roles) {
        Account account = new Account();
        account.setId(UUID.randomUUID());
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        // Never true here, and not merely "false by default": a reservation that arrived enabled would
        // be a live login for a customer ukun-api has not written yet (skill `spring-auth`, rule 4).
        account.setEnabled(false);
        // A mutable copy, not Set.copyOf: Hibernate replaces this collection with its own tracking one
        // when the entity is persisted, and handing it an immutable set is how that turns into an
        // UnsupportedOperationException at flush time rather than at compile time.
        account.setRoles(new LinkedHashSet<>(roles));

        RegistrationRequest registration = new RegistrationRequest();
        registration.setIdempotencyKey(idempotencyKey);
        registration.setAccountId(account.getId());

        accounts.save(account);
        registrations.save(registration);
        accounts.flush();
        registrations.flush();

        return account.getId();
    }
}
