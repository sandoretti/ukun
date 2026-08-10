package dev.sandoretti.ukun.api.identity;

import java.util.Optional;

/**
 * What the {@code identity} module offers the rest of the application: the person behind the current
 * request, and nothing else. One of the two types other modules may import from here
 * ({@code docs/14-modules.md} §1), alongside {@link AccountsPort}.
 *
 * <p>It exists so that no other module ever touches {@code SecurityContextHolder}, a
 * {@code JwtAuthenticationToken} or a claim name. That matters beyond tidiness: the moment a use case
 * reads {@code jwt.getClaim("sub")} on its own, the rule of {@code docs/08-identidad-y-seguridad.md}
 * §4 — an identifier in a request identifies, it never authorises — becomes a convention instead of
 * something the code makes hard to get wrong.
 */
public interface IdentityApi {

    /**
     * The account behind this request, or empty on a public endpoint nobody authenticated for.
     *
     * <p>Use this only where being anonymous is a legitimate outcome. Anywhere the filter chain
     * already requires authentication, {@link #requireCurrentAccount()} says so more clearly than an
     * {@code orElseThrow} repeated at every call site.
     */
    Optional<CurrentAccount> currentAccount();

    /**
     * The account behind this request, on an endpoint that cannot be reached without one.
     *
     * @throws org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
     *     if there is no authenticated account — which means the route was left out of the filter
     *     chain's protected set, a configuration bug, not a client one.
     */
    CurrentAccount requireCurrentAccount();
}
