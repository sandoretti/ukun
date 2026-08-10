package dev.sandoretti.ukun.gateway.security;

import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/**
 * The logged-in person, as this gateway needs to see them: the standard OIDC user plus the two things
 * that live in the <em>access</em> token and nowhere else — the roles the coarse filter of ADR-0020
 * decides on, and the account id {@code docs/11-observabilidad-y-logging.md} §3 requires on the
 * {@code auth.logout} event.
 *
 * <p>Why they are not simply read off the id token is explained in {@link AccessTokenOidcUserService}.
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
}
