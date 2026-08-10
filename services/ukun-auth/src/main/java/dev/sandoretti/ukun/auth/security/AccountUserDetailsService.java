package dev.sandoretti.ukun.auth.security;

import dev.sandoretti.ukun.auth.application.AccountCredentials;
import dev.sandoretti.ukun.auth.application.AccountsService;
import dev.sandoretti.ukun.auth.domain.Role;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Turns an account into something Spring Security can authenticate. It goes through
 * {@link AccountsService} and never through {@code AccountRepository}: what comes back is a record,
 * so no lazy association can escape a closed session into the middle of a login.
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

    private final AccountsService accounts;

    AccountUserDetailsService(AccountsService accounts) {
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
