package dev.sandoretti.ukun.api.stores;

/**
 * What the {@code stores} module offers the rest of the application. The only type here that other
 * modules may import — everything else lives in a subpackage and is invisible to them
 * ({@code docs/14-modules.md} §1).
 *
 * <p>It returns no {@code @Entity} and takes none, which is the golden rule of §2 of that document
 * and an ArchUnit rule besides. Today that means a primitive answer to a primitive question; when
 * {@code inventory}, {@code customers} or {@code staff} need the name of a store, this interface
 * grows a batch method returning a {@code record}, never {@code Store}.
 */
public interface StoresApi {

    /**
     * Whether a store with this identifier exists.
     *
     * <p>The caller is {@code GET /api/availability}, which has to answer {@code 404} for a store
     * nobody has, and the {@code favoriteStoreId} of RF-05 later. It is deliberately a question and
     * not a fetch: an existence check that returns the store invites the caller to read fields it was
     * not given, and every one of those fields would then be a dependency of the other module.
     *
     * <p>{@code null} is {@code false} rather than an exception. A missing identifier is the caller's
     * validation problem, not this module's, and the callers all reject it before asking.
     */
    boolean exists(Long storeId);
}
