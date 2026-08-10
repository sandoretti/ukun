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
 * The claim-to-authority mapping the coarse filter of ADR-0020 stands on.
 *
 * <p>It gets a test of its own because the full path — a real callback, a real token endpoint, a real
 * JWKS — is not something an {@code *IT} in this service can exercise, and because the failure it
 * guards against is quiet: an administrator who logs in successfully and is then refused
 * {@code /api/admin/**} by the very gateway that just authenticated them.
 */
class AccessTokenOidcUserServiceTest {

    @Test
    @DisplayName("lifts the roles claim of the access token into ROLE_ authorities")
    void liftsTheRolesClaimIntoAuthorities() {
        Jwt accessToken = accessTokenWithRoles(List.of("EMPLOYEE", "ADMIN"));

        assertThat(AccessTokenOidcUserService.allAuthoritiesOf(person(), accessToken))
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_EMPLOYEE", "ROLE_ADMIN");
    }

    /**
     * The scope authorities the standard service produced are kept rather than replaced. Losing them
     * would be invisible today and would quietly remove the ability to reason about scope later.
     */
    @Test
    @DisplayName("keeps the authorities the OIDC user already had")
    void keepsTheAuthoritiesTheUserAlreadyHad() {
        Jwt accessToken = accessTokenWithRoles(List.of("CUSTOMER"));

        assertThat(AccessTokenOidcUserService.allAuthoritiesOf(person(), accessToken))
                .extracting(GrantedAuthority::getAuthority)
                .contains("SCOPE_openid", "ROLE_CUSTOMER");
    }

    /**
     * A token with no {@code roles} leaves the caller authenticated with no {@code ROLE_*}, which the
     * coarse filter refuses. That is the safe direction: the alternative — failing the login — would
     * look to a user exactly like the authorization server being down.
     */
    @Test
    @DisplayName("yields no role when the claim is absent, rather than failing")
    void yieldsNoRoleWhenTheClaimIsAbsent() {
        Jwt accessToken = Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .subject("8f2c1e40-3b7a-4d21-9a55-1f0c9d2e6b31")
                .build();

        assertThat(AccessTokenOidcUserService.allAuthoritiesOf(person(), accessToken))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("SCOPE_openid");
    }

    private static Jwt accessTokenWithRoles(List<String> roles) {
        return Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .subject("8f2c1e40-3b7a-4d21-9a55-1f0c9d2e6b31")
                .claim("roles", roles)
                .build();
    }

    /** As the standard OIDC service would hand it over: scope authorities, and no role in sight. */
    private static OidcUser person() {
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token")
                .subject("customer@ukun.dev")
                .build();

        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("SCOPE_openid")), idToken, "sub");
    }
}
