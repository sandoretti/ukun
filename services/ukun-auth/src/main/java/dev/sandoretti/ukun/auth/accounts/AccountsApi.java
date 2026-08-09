package dev.sandoretti.ukun.auth.accounts;

import java.util.Optional;

/**
 * The public API of the {@code accounts} module — the only type other modules may call, and the
 * reason {@code oauth2} never sees a repository or an entity (docs/14-modules.md §1 and §2).
 *
 * <p>It is deliberately narrow. Everything else this module will do — reserving, activating and
 * releasing an account for the signup saga — arrives with issue #25 and belongs on this same
 * interface, not on a second one.
 */
public interface AccountsApi {

    /**
     * Looks an account up by its login credential. Case-insensitivity is not implemented here: the
     * column is {@code CITEXT}, so the guarantee lives in the type and no caller can forget it
     * (docs/10-modelo-de-datos.md §2).
     *
     * <p>Returns the account whether or not it is enabled. Deciding what a disabled account means is
     * authentication's job, not this module's — and answering "no such account" for a reserved one
     * would make the two indistinguishable to the code that has to log the difference
     * ({@code reason=CREDENTIALS} vs {@code DISABLED}, docs/08 §8).
     */
    Optional<AccountCredentials> findCredentialsByEmail(String email);
}
