package dev.sandoretti.ukun.api.customers.application;

import dev.sandoretti.ukun.api.shared.error.FieldValidationException;
import dev.sandoretti.ukun.api.stores.StoresApi;
import org.springframework.stereotype.Component;

/**
 * The one rule this module applies to {@code customer.favorite_store_id}, in the one place both writers
 * of that column reach for it: the signup saga and the profile edit.
 *
 * <p>It is a class rather than a line repeated twice because the two halves that make it correct are
 * easy to get right once and easy to forget the second time. <strong>The check happens before anything
 * is written</strong>, so a wrong identifier comes back as a {@code 400} naming the field instead of a
 * foreign key violation flattened into a {@code 409} with nothing to point at
 * ({@code docs/08-identidad-y-seguridad.md} §3, step 0). And <strong>{@code null} is a value, not a
 * missing one</strong>: no favourite store is a legitimate state, and clearing one is how a customer
 * undoes the choice.
 *
 * <p>The message is part of the rule and not decoration: the frontend paints it under the same field in
 * both flows, and two copies of a string are two chances for the signup and the profile to disagree
 * about what went wrong with the same value.
 *
 * <p>It does not answer "does this store exist" — {@link StoresApi} does, and it is the only door
 * {@code customers} has into {@code stores} ({@code docs/14-modules.md} §1). What lives here is what to
 * do about the answer.
 */
@Component
class FavoriteStores {

    private static final String FIELD = "favoriteStoreId";

    private final StoresApi stores;

    FavoriteStores(StoresApi stores) {
        this.stores = stores;
    }

    /**
     * @param favoriteStoreId the identifier as it arrived, {@code null} included
     * @throws FieldValidationException if it names a store that does not exist
     */
    void requireExisting(Long favoriteStoreId) {
        if (favoriteStoreId != null && !stores.exists(favoriteStoreId)) {
            throw new FieldValidationException(FIELD, "does not match an existing store");
        }
    }
}
