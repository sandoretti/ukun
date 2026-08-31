package dev.sandoretti.ukun.api.customers.application;

import dev.sandoretti.ukun.api.audit.Actor;
import dev.sandoretti.ukun.api.audit.AuditTrail;
import dev.sandoretti.ukun.api.audit.AuditedEvent;
import dev.sandoretti.ukun.api.identity.AccountRegistrationService;
import dev.sandoretti.ukun.api.identity.CreateAccountCommand;
import dev.sandoretti.ukun.api.identity.EmailAlreadyExistsException;
import dev.sandoretti.ukun.api.identity.Registration;
import dev.sandoretti.ukun.api.identity.RegistrationOutcome;
import dev.sandoretti.ukun.api.identity.Role;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * {@code POST /api/signup} (RF-06): the customer's half of the registration saga of
 * {@code docs/08-identidad-y-seguridad.md} §3.
 *
 * <p>The saga itself — reserve the email, write locally, activate, and what to do when any of the
 * three fails — belongs to {@link AccountRegistrationService}, because the employee's alta of RF-11
 * runs the very same one and {@code staff} cannot reach into this module (ADR-0017 §1). What is left
 * here is what is genuinely the customer's: the local rules to check before anything is reserved, the
 * {@code CUSTOMER} role the account is born with, the profile written in a single transaction by
 * {@link CustomerRegistrar}, and the {@code signup.completed} event that names the row this module
 * owns.
 *
 * <p><strong>Still deliberately not {@code @Transactional}</strong>, and for the same reason as before:
 * the saga makes two calls over the network, so the only transaction in the flow is
 * {@link CustomerRegistrar}'s.
 *
 * <p>That is also why {@code signup.completed} is the one audited event of this service whose row does
 * <strong>not</strong> join the transaction of the change it audits — there is none to join here, and
 * {@link AuditTrail} opens its own. The guarantee ADR-0014 asks for survives anyway, in the only
 * direction that matters: by the time this line runs the profile has already committed, so the row can
 * be missing (if the insert fails) but never orphaned from a change that was rolled back.
 *
 * <p>The actor is stated explicitly rather than read from the request, because there is no token to
 * read: {@code POST /api/signup} is {@code permitAll} ({@code docs/08} §3). Whoever the trail names for
 * this row, it is the account the saga has just created.
 */
@Service
public class SignupService {

    /** A signup creates a customer. Employees are created by the back-office (RF-11, {@code staff}). */
    private static final Set<Role> CUSTOMER_ONLY = Set.of(Role.CUSTOMER);

    private final AccountRegistrationService registrations;
    private final CustomerRegistrar registrar;
    private final FavoriteStores favoriteStores;
    private final AuditTrail auditTrail;

    SignupService(
            AccountRegistrationService registrations,
            CustomerRegistrar registrar,
            FavoriteStores favoriteStores,
            AuditTrail auditTrail) {

        this.registrations = registrations;
        this.registrar = registrar;
        this.favoriteStores = favoriteStores;
        this.auditTrail = auditTrail;
    }

    /**
     * @return {@link RegistrationOutcome#COMPLETED} for the {@code 201},
     *     {@link RegistrationOutcome#ACTIVATION_PENDING} for the {@code 202}
     * @throws EmailAlreadyExistsException propagated from ukun-auth, which the frontend paints under
     *     the email field
     */
    public RegistrationOutcome signUp(SignupRequest request) {
        validateLocally(request);

        Registration registration = registrations.register(
                new CreateAccountCommand(request.email(), request.password(), CUSTOMER_ONLY, false),
                accountId -> registrar.register(accountId, request));

        if (registration.outcome() == RegistrationOutcome.COMPLETED) {
            auditTrail.record(
                    AuditedEvent.SIGNUP_COMPLETED,
                    new Actor(String.valueOf(registration.accountId()), Role.CUSTOMER.name()),
                    registration.localId(),
                    Map.of(
                            "registrationId", registration.registrationId(),
                            "accountId", registration.accountId(),
                            "customerId", registration.localId()));
        }

        return registration.outcome();
    }

    /**
     * Step 0: the rules this service can decide on its own. Email uniqueness is deliberately not among
     * them — it does not know, and asking ukun-auth first would reintroduce the legacy's
     * check-then-write race across a network as well.
     */
    private void validateLocally(SignupRequest request) {
        favoriteStores.requireExisting(request.favoriteStoreId());
    }
}
