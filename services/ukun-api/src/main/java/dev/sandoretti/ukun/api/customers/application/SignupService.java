package dev.sandoretti.ukun.api.customers.application;

import dev.sandoretti.ukun.api.identity.AccountRegistrationService;
import dev.sandoretti.ukun.api.identity.CreateAccountCommand;
import dev.sandoretti.ukun.api.identity.EmailAlreadyExistsException;
import dev.sandoretti.ukun.api.identity.Registration;
import dev.sandoretti.ukun.api.identity.RegistrationOutcome;
import dev.sandoretti.ukun.api.identity.Role;
import dev.sandoretti.ukun.api.shared.observability.BusinessEvents;
import java.util.Map;
import java.util.Set;
import org.slf4j.event.Level;
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
 */
@Service
public class SignupService {

    /** A signup creates a customer. Employees are created by the back-office (RF-11, {@code staff}). */
    private static final Set<Role> CUSTOMER_ONLY = Set.of(Role.CUSTOMER);

    private final AccountRegistrationService registrations;
    private final CustomerRegistrar registrar;
    private final FavoriteStores favoriteStores;

    SignupService(
            AccountRegistrationService registrations, CustomerRegistrar registrar, FavoriteStores favoriteStores) {

        this.registrations = registrations;
        this.registrar = registrar;
        this.favoriteStores = favoriteStores;
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
            BusinessEvents.emit(
                    Level.INFO,
                    "signup.completed",
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
