package dev.sandoretti.ukun.api.stores;

import java.util.Map;
import java.util.Set;

/**
 * What the {@code stores} module offers the rest of the application. The only type here that other
 * modules may import — everything else lives in a subpackage and is invisible to them
 * ({@code docs/14-modules.md} §1).
 *
 * <p>It returns no {@code @Entity} and takes none, which is the golden rule of §2 of that document
 * and an ArchUnit rule besides. Two of its three methods are a primitive answer to a primitive
 * question; the third is the batch of {@link StoreSummary} this interface always said it would grow
 * the day another module needed to name a store to a person — {@code staff} was the first, for the
 * {@code storeName} of {@code GET /api/me/employment}.
 */
public interface StoresApi {

    /**
     * Whether a store with this identifier exists.
     *
     * <p>The callers are {@code GET /api/availability}, which has to answer {@code 404} for a store
     * nobody has, and the signup saga, which validates {@code favoriteStoreId} before writing anything
     * so that a wrong value is a {@code 400} on the field rather than a foreign key violation
     * ({@code docs/08-identidad-y-seguridad.md} §3, step 0). It is deliberately a question and
     * not a fetch: an existence check that returns the store invites the caller to read fields it was
     * not given, and every one of those fields would then be a dependency of the other module.
     *
     * <p>{@code null} is {@code false} rather than an exception. A missing identifier is the caller's
     * validation problem, not this module's, and the callers all reject it before asking.
     */
    boolean exists(Long storeId);

    /**
     * The identifier of the store the shop sells from online.
     *
     * <p>RF-04 makes it a business rule rather than a default: an order is placed against the online
     * store's stock and never against the customer's favourite one. {@code inventory} asks for it to
     * answer what a cart line has available, and checkout will ask for the same reason.
     *
     * <p><strong>Resolved by {@code store.is_online}, never by name.</strong> The legacy looks it up as
     * {@code tienda.nombre = 'Ukun Online'} from inside a bean constructor and refuses to start when the
     * row is missing — defect #6, and both halves of it are wrong. The flag carries the invariant
     * ({@code ux_store_online}, a partial unique index, makes a second online store impossible), and the
     * look-up happens inside the business transaction that needs it.
     *
     * @throws IllegalStateException if no store is marked online. It is not a client error and not a
     *     {@code 404}: a shop with nowhere to sell from is an installation that was never finished, so
     *     it is a {@code 500} with a stack trace, which is what gets it fixed.
     */
    Long onlineStoreId();

    /**
     * What the given stores are called, keyed by identifier.
     *
     * <p><strong>A batch and not a look-up per store</strong>, for the same reason
     * {@code CatalogApi.summariesByIds} is one: a caller holding several identifiers — the stores of a
     * page of employees, say — would otherwise issue a statement per row, which is the N+1 this kind
     * of interface exists to make impossible. Its only caller today asks about one store, and asking
     * about one is the degenerate case of asking about several rather than a different method.
     *
     * <p>An identifier nobody has is simply absent from the map, not a {@code null} value and not an
     * exception: this answers what exists, and what a missing entry means is the caller's to decide.
     * {@link #exists(Long)} is still the method for asking whether a store is there, because a caller
     * that only wants the answer to that question should not be reading names to find out.
     */
    Map<Long, StoreSummary> summariesByIds(Set<Long> storeIds);
}
