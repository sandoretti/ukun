package dev.sandoretti.ukun.auth.oauth2.internal;

import dev.sandoretti.ukun.auth.accounts.AccountCredentials;
import dev.sandoretti.ukun.auth.accounts.AccountsApi;
import dev.sandoretti.ukun.auth.accounts.Role;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Turns an account into something Spring Security can authenticate. It goes through
 * {@link AccountsApi} and never through the repository — that is the boundary
 * {@code ApplicationModules.verify()} enforces (docs/14-modules.md §2).
 *
 * <p>The username is the email, because that is what a person types. It is <em>not</em> what ends up
 * in the {@code sub} claim: {@link TokenClaimsCustomizer} replaces it with the account's UUID,
 * because an identity that changes when somebody edits their email address would silently reassign
 * every resource {@code ukun-api} resolves by {@code account_id}.
 *
 * <p>Throwing {@link UsernameNotFoundException} for an unknown email is not an information leak
 * here: {@code DaoAuthenticationProvider} hides it behind {@code BadCredentialsException} by
 * default, which is exactly the generic response {@code docs/08} §8 asks for. The distinction
 * survives only in the log.
 */
@Service
class AccountUserDetailsService implements UserDetailsService {

    private final AccountsApi accounts;

    AccountUserDetailsService(AccountsApi accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        AccountCredentials account =
                accounts.findCredentialsByEmail(email).orElseThrow(() -> new UsernameNotFoundException(email));

        return User.withUsername(account.email())
                .password(account.passwordHash())
                // A reserved account (enabled = false) is not a login failure of a different kind: it
                // is an account that does not work yet, and DisabledException is what lets the log
                // record `reason=DISABLED` while the response stays identical.
                .disabled(!account.enabled())
                .authorities(authoritiesOf(account))
                .build();
    }

    /**
     * {@code ROLE_} prefix because that is what {@code hasRole()} prepends on the other side, in
     * {@code ukun-api}'s {@code @PreAuthorize}. The {@code roles} claim of the token carries the bare
     * names instead — a claim is a contract with another service, not a Spring convention.
     */
    private static List<SimpleGrantedAuthority> authoritiesOf(AccountCredentials account) {
        return account.roles().stream()
                .map(Role::name)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
