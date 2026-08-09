package dev.sandoretti.ukun.auth.accounts.internal;

import dev.sandoretti.ukun.auth.accounts.AccountCredentials;
import dev.sandoretti.ukun.auth.accounts.AccountsApi;
import dev.sandoretti.ukun.auth.accounts.domain.Account;
import dev.sandoretti.ukun.auth.accounts.infra.AccountRepository;
import java.util.LinkedHashSet;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only implementation of {@link AccountsApi}. It exists to keep the entity inside the module:
 * the mapping to {@link AccountCredentials} happens here, inside the transaction, so no lazy
 * association can escape into a caller that has no session (docs/14-modules.md §2).
 *
 * <p>{@code @Transactional(readOnly = true)} at class level, per the skill {@code spring-api}: a
 * read-only transaction is the default and a writing method opts out explicitly, rather than the
 * other way round.
 */
@Service
@Transactional(readOnly = true)
class JpaAccountsService implements AccountsApi {

    private final AccountRepository accounts;

    JpaAccountsService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public Optional<AccountCredentials> findCredentialsByEmail(String email) {
        return accounts.findByEmail(email).map(JpaAccountsService::toCredentials);
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
