package dev.sandoretti.ukun.api.customers.application;

import dev.sandoretti.ukun.api.audit.AuditTrail;
import dev.sandoretti.ukun.api.audit.AuditedEvent;
import dev.sandoretti.ukun.api.customers.domain.Customer;
import dev.sandoretti.ukun.api.customers.infra.CustomerRepository;
import dev.sandoretti.ukun.api.identity.CurrentAccount;
import dev.sandoretti.ukun.api.shared.error.ResourceNotFoundException;
import dev.sandoretti.ukun.api.shared.observability.BusinessEvents;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.event.Level;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The customer's own profile (RF-05): {@code GET} and {@code PUT /api/me/profile}.
 *
 * <p><strong>Every method here starts from {@code accountId} and nothing else.</strong> There is no
 * overload taking a customer id, and that is the design: an identifier arriving in a request identifies
 * a resource and never authorises access to it ({@code docs/08-identidad-y-seguridad.md} §4). With the
 * owner resolved server-side from the token there is no parameter left for a caller to tamper with —
 * the route has none — so the rule is enforced by the shape of the code rather than by a check somebody
 * has to remember to write.
 *
 * <p>{@code @Transactional(readOnly = true)} at class level with {@link #update} overriding it, and an
 * empty constructor: {@code open-in-view: false} makes a lazy access outside the transaction fail
 * loudly, and rule 6 of the skill {@code spring-api} keeps look-ups out of bean construction.
 */
@Service
@Transactional(readOnly = true)
public class MyProfileService {

    /**
     * Not audited, so its name still lives here: which store somebody prefers is a convenience of the
     * catalogue, not a change to a resource anyone can claim later ({@code docs/11} §3).
     */
    private static final String FAVORITE_STORE_CHANGED = "favorite_store.changed";

    private static final String FAVORITE_STORE_FIELD = "favoriteStoreId";
    private static final String CUSTOMER_ID = "customerId";

    private final CustomerRepository customers;
    private final ProfileMapper mapper;
    private final FavoriteStores favoriteStores;
    private final AuditTrail auditTrail;

    public MyProfileService(
            CustomerRepository customers,
            ProfileMapper mapper,
            FavoriteStores favoriteStores,
            AuditTrail auditTrail) {

        this.customers = customers;
        this.mapper = mapper;
        this.favoriteStores = favoriteStores;
        this.auditTrail = auditTrail;
    }

    /**
     * <strong>Why a missing profile is a {@code 500} here and a {@code 404} in {@link #update}.</strong>
     * The contract declares no {@code 404} for the read, and it is right not to: a token with
     * {@code CUSTOMER} and no row behind it is a broken invariant of this service rather than something
     * the caller asked for and did not get — the reasoning lives on {@link MissingProfileException}, now
     * that the cart reaches the same conclusion by another route. The write declares its {@code 404} in
     * the contract and can therefore answer it.
     */
    public CustomerProfileResponse view(CurrentAccount account) {
        Customer customer = customers.findByAccountId(account.accountId())
                .orElseThrow(() -> new MissingProfileException(account.accountId()));

        return mapper.toResponse(customer, account.email());
    }

    @Transactional
    public CustomerProfileResponse update(CurrentAccount account, UpdateProfileRequest request) {
        Customer customer = customers.findByAccountId(account.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", account.accountId()));

        favoriteStores.requireExisting(request.favoriteStoreId());

        // RF-05: a form saved without edits is reported, not written. Asked before anything is applied,
        // because afterwards there is nothing left to compare against.
        List<String> changed = ProfileChanges.between(customer, request);
        if (changed.isEmpty()) {
            return mapper.toResponse(customer, account.email());
        }

        Long previousStore = customer.getFavoriteStoreId();

        mapper.apply(request, customer);
        // The two rows the profile owns, edited in place rather than replaced — see ProfileMapper.
        mapper.apply(request.address(), customer.getAddress());
        mapper.apply(request.card(), customer.getCard());

        emitUpdated(customer.getId(), changed);
        if (changed.contains(FAVORITE_STORE_FIELD)) {
            emitFavoriteStoreChanged(customer.getId(), previousStore, customer.getFavoriteStoreId());
        }

        return mapper.toResponse(customer, account.email());
    }

    /**
     * Field names, never their values: a profile is personal data ({@code docs/11} §4). That rule is why
     * this event can be audited at all — {@code details} is an open JSONB column, and what goes in it
     * here is {@code ["address","card"]}, not what the address or the card now say.
     */
    private void emitUpdated(long customerId, List<String> changedFields) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(CUSTOMER_ID, customerId);
        fields.put("changedFields", changedFields);

        auditTrail.record(AuditedEvent.PROFILE_UPDATED, customerId, fields);
    }

    /**
     * The one exception to the rule above, and it is in the closed table too: the previous and the new
     * store are identifiers of public rows, not personal data, and without the previous one the line
     * does not say what happened.
     */
    private static void emitFavoriteStoreChanged(long customerId, Long previousStoreId, Long newStoreId) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(CUSTOMER_ID, customerId);
        fields.put("previousStoreId", previousStoreId);
        fields.put("newStoreId", newStoreId);

        BusinessEvents.emit(Level.INFO, FAVORITE_STORE_CHANGED, fields);
    }
}
