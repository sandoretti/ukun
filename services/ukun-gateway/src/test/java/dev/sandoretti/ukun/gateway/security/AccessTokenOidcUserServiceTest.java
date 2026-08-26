package dev.sandoretti.ukun.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * What the principal takes from each of the two tokens, which is the trap this class exists to keep
 * in one place: the person's <em>name</em> comes from the id token and is the email they typed, while
 * the account they are logged as comes from the access token and is a UUID.
 *
 * <p>It gets a unit test because the full path — a real callback, a real token endpoint, a real JWKS —
 * is not something an {@code *IT} of this service can exercise, and because the failure is quiet:
 * every {@code auth.logout} line and every access log line would silently name the wrong thing, or
 * name a person by email where {@code docs/11-observabilidad-y-logging.md} §5 says it must not.
 *
 * <p>Nothing here is about roles any more. They are not lifted into the principal at all since issue
 * #145 — the coarse filter of ADR-0020 reads them off the relayed token on each request
 * ({@code AdminAuthorizationIT}), and a second copy in the session is what drifted.
 */
class AccessTokenOidcUserServiceTest {

    private static final String ACCOUNT_ID = "8f2c1e40-3b7a-4d21-9a55-1f0c9d2e6b31";

    @Test
    @DisplayName("names the account from the access token's sub, not the id token's")
    void namesTheAccountFromTheAccessToken() {
        UkunOidcUser principal = AccessTokenOidcUserService.withAccountId(person(), accessToken(), "sub");

        assertThat(principal.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(principal.getName()).isEqualTo("customer@ukun.dev");
    }

    /**
     * The authorities the standard service produced are carried over rather than replaced. Losing them
     * would be invisible today and would quietly remove the ability to reason about scope later.
     */
    @Test
    @DisplayName("keeps the authorities the OIDC user already had, and adds none")
    void keepsTheAuthoritiesTheUserAlreadyHad() {
        UkunOidcUser principal = AccessTokenOidcUserService.withAccountId(person(), accessToken(), "sub");

        assertThat(principal.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("SCOPE_openid");
    }

    /**
     * A role in the token changes nothing about the principal. Written as a test rather than left
     * implicit because putting it back is a one-line change that no other test in this module would
     * fail on — and it is the change that reopens issue #145.
     */
    @Test
    @DisplayName("does not lift the roles claim into authorities")
    void doesNotLiftTheRolesClaim() {
        Jwt withRoles = Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .subject(ACCOUNT_ID)
                .claim("roles", List.of("EMPLOYEE", "ADMIN"))
                .build();

        UkunOidcUser principal = AccessTokenOidcUserService.withAccountId(person(), withRoles, "sub");

        assertThat(principal.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .doesNotContain("ROLE_ADMIN", "ROLE_EMPLOYEE");
    }

    private static Jwt accessToken() {
        return Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .subject(ACCOUNT_ID)
                .build();
    }

    /** As the standard OIDC service hands it over: scope authorities, and the email as the name. */
    private static OidcUser person() {
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token")
                .subject("customer@ukun.dev")
                .build();

        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("SCOPE_openid")), idToken, "sub");
    }
}
