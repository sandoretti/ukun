package dev.sandoretti.ukun.api.stores.application;

import dev.sandoretti.ukun.api.stores.StoreSummary;
import dev.sandoretti.ukun.api.stores.StoresApi;
import dev.sandoretti.ukun.api.stores.domain.Store;
import dev.sandoretti.ukun.api.stores.infra.StoreRepository;
import dev.sandoretti.ukun.api.stores.infra.StoreSpecifications;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read side of {@code store} (RF-02, {@code listStores} of the contract), and the implementation
 * of {@link StoresApi} that other modules see.
 *
 * <p>Both in one class because they are one query away from each other and the module has exactly one
 * table. The split that matters is not service-per-caller: it is that {@link StoresApi} is the only
 * type outside {@code stores} can name, so {@code inventory} depends on three methods that answer
 * three questions and cannot reach {@link #list} or a {@code Store} through it.
 *
 * <p>{@code @Transactional(readOnly = true)} at class level and nothing in the constructor but
 * assignments, for the same two reasons as every other query service here: {@code open-in-view: false}
 * turns a lazy access outside the transaction into a loud failure instead of a silent N+1, and rule 6
 * of the skill {@code spring-api} keeps look-ups out of bean construction — the legacy has two
 * services that refuse to start when one row is missing (defect #6).
 */
@Service
@Transactional(readOnly = true)
public class StoreQueryService implements StoresApi {

    /**
     * "Stores, sorted by name", as the contract's {@code 200} says, with {@code id} as the last
     * criterion.
     *
     * <p>The tie-breaker holds even though {@code ux_store_name} makes a tie impossible today: defect
     * #23 is the rule that <em>every</em> {@code Sort} in this API is total, and a rule with an
     * exception for the cases someone judged safe is the rule that gets forgotten in the case that
     * was not.
     */
    private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id"));

    private final StoreRepository stores;
    private final StoreMapper mapper;

    public StoreQueryService(StoreRepository stores, StoreMapper mapper) {
        this.stores = stores;
        this.mapper = mapper;
    }

    /**
     * The whole listing, or the subset matching {@code q}. A blank {@code q} is no filter rather than
     * a search for the empty string — the contract makes the parameter optional and {@code minLength:
     * 1}, so the two cases the client can produce both mean "everything".
     */
    public List<StoreResponse> list(String query) {
        List<Specification<Store>> conditions = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            conditions.add(StoreSpecifications.matches(query));
        }

        return stores.findAll(Specification.allOf(conditions), BY_NAME).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public boolean exists(Long storeId) {
        return storeId != null && stores.existsById(storeId);
    }

    /**
     * {@link StoresApi#onlineStoreId()}. One indexed look-up, inside whatever transaction the caller is
     * already in — not a field resolved once at startup, which is the constructor query of defect #6
     * wearing a different hat.
     */
    @Override
    public Long onlineStoreId() {
        return stores.findOnlineStoreId()
                .orElseThrow(() -> new IllegalStateException("No store is marked as the online one"));
    }

    /**
     * {@link StoresApi#summariesByIds(Set)}: what the modules that hold a {@code store_id} are allowed
     * to know about it.
     *
     * <p>{@code findAllById} rather than a query of this module's own, exactly as
     * {@code ProductQueryService} does for its own summaries: one statement for the whole batch, served
     * by the primary key, and without the {@code @EntityGraph} the two overrides in
     * {@link StoreRepository} carry — which here is the point rather than an omission, since
     * {@link StoreSummary} has no address and fetching that association would be a join nobody reads.
     *
     * <p>An empty request is answered without touching the database. Spring Data would return an empty
     * list anyway, but a statement issued to learn nothing is still a statement.
     */
    @Override
    public Map<Long, StoreSummary> summariesByIds(Set<Long> storeIds) {
        if (storeIds.isEmpty()) {
            return Map.of();
        }

        return stores.findAllById(storeIds).stream()
                .map(mapper::toSummary)
                .collect(Collectors.toMap(StoreSummary::id, Function.identity()));
    }
}
