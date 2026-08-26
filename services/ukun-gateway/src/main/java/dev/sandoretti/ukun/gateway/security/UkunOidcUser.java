package dev.sandoretti.ukun.gateway.security;

import java.security.Principal;
import java.util.Collection;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/**
 * The logged-in person, as this gateway needs to see them: the standard OIDC user plus the one thing
 * that lives in the <em>access</em> token and nowhere else — the account id
 * {@code docs/11-observabilidad-y-logging.md} §3 requires on the {@code auth.logout} event and on the
 * access log.
 *
 * <p>Why it is not simply read off the id token is explained in {@link AccessTokenOidcUserService}.
 * The roles are <strong>not</strong> here and must not be put here: the coarse filter of ADR-0020
 * reads them off the token it relays, on the request that relays it, and a second copy in the session
 * is exactly what drifted in issue #145.
 *
 * <p>{@code getName()} is left alone, so the principal is still named by the id token's {@code sub} —
 * the email the person typed into the login form. That is what keys the stored authorized client, and
 * changing it to the account id would buy nothing here while quietly changing where every session's
 * token is filed. The account id is what the audit event uses, and it is available through
 * {@link #accountId()} for exactly that.
 *
 * <p>That distinction is why this type is public rather than package private. Everything in this
 * service that names the person in a log has to reach {@link #accountId()} and must <em>not</em> reach
 * {@code getName()} — {@code docs/11-observabilidad-y-logging.md} §5 keeps email out of the MDC
 * precisely because it would otherwise appear on every line of every request. There are two such
 * callers now, {@code LogoutEventLogger} here and the access log in {@code web}, and the second one
 * lives in another package.
 */
public final class UkunOidcUser extends DefaultOidcUser {

    private final String accountId;

    public UkunOidcUser(
            Collection<? extends GrantedAuthority> authorities,
            OidcIdToken idToken,
            String nameAttributeKey,
            String accountId) {

        super(authorities, idToken, nameAttributeKey);
        this.accountId = accountId;
    }

    /** The {@code sub} of the access token: the account's UUID, not the email ({@code docs/08} §4). */
    public String accountId() {
        return accountId;
    }

    /**
     * The account behind a principal, when there is one. It lives here rather than in each caller
     * because the knowledge it encodes is exactly the trap this class documents — the account is on
     * this type and <strong>not</strong> in {@code getName()} — and that is worth knowing in one
     * place instead of two.
     *
     * <p>It returns an {@code Optional} rather than a placeholder because its two callers want
     * different things from the absence: the {@code auth.logout} event says {@code unknown}, since an
     * audit line that admits what it does not know beats a missing one, while the access log leaves
     * the field out, since most of the catalogue is served to nobody in particular.
     *
     * <p>Anything that is not this gateway's own user is absent too, including a principal whose
     * {@code getName()} would happily return something. Guessing is how the email would get in.
     */
    public static Optional<String> accountIdOf(Principal principal) {
        return principal instanceof Authentication authentication
                        && authentication.getPrincipal() instanceof UkunOidcUser user
                ? Optional.of(user.accountId())
                : Optional.empty();
    }
}
