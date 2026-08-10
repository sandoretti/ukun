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
 */
final class UkunOidcUser extends DefaultOidcUser {

    private final String accountId;

    UkunOidcUser(
            Collection<? extends GrantedAuthority> authorities,
            OidcIdToken idToken,
            String nameAttributeKey,
            String accountId) {

        super(authorities, idToken, nameAttributeKey);
        this.accountId = accountId;
    }

    /** The {@code sub} of the access token: the account's UUID, not the email ({@code docs/08} §4). */
    String accountId() {
        return accountId;
    }
}
