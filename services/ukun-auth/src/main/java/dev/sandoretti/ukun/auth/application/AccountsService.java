package dev.sandoretti.ukun.auth.application;

import dev.sandoretti.ukun.auth.domain.Account;
import dev.sandoretti.ukun.auth.infra.AccountRepository;
import java.util.LinkedHashSet;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads accounts, and is the reason {@link Account} never leaves this layer: the mapping to
 * {@link AccountCredentials} happens here, inside the transaction, so no lazy association can escape
 * into a caller that has no session.
 *
 * <p>That property is <strong>enforced</strong>, not merely intended — the fourth of the seven
 * ArchUnit rules of {@code docs/03-analisis-y-patrones.md} §3 fails the build if a public method of a
 * {@code @Service} returns an {@code @Entity}. It was never Modulith's doing, which is part of why
 * ADR-0022 could remove it without losing this.
 *
 * <p>A concrete class and not an interface with one implementation: it had one as long as it was the
 * public API of a Modulith module, and without modules there is nothing left for it to encapsulate.
 * Issue #25 adds reserve/activate/release for the signup saga here, not on a second type.
 *
 * <p>{@code @Transactional(readOnly = true)} at class level, per the skill {@code spring-api}: a
 * read-only transaction is the default and a writing method opts out explicitly, rather than the
 * other way round.
 */
@Service
@Transactional(readOnly = true)
public class AccountsService {

    private final AccountRepository accounts;

    AccountsService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /**
     * Looks an account up by its login credential. Case-insensitivity is not implemented here: the
     * column is {@code CITEXT}, so the guarantee lives in the type and no caller can forget it
     * (docs/10-modelo-de-datos.md §2).
     *
     * <p>Returns the account whether or not it is enabled. Deciding what a disabled account means is
     * authentication's job — and answering "no such account" for a reserved one would make the two
     * indistinguishable to the code that has to log the difference ({@code reason=CREDENTIALS} vs
     * {@code DISABLED}, docs/08 §8).
     */
    public Optional<AccountCredentials> findCredentialsByEmail(String email) {
        return accounts.findByEmail(email).map(AccountsService::toCredentials);
    }

    private static AccountCredentials toCredentials(Account account) {
        return new AccountCredentials(
                account.getId(),
                account.getEmail(),
                account.getPasswordHash(),
                account.isEnabled(),
                new LinkedHashSet<>(account.getRoles()));
    }
}
