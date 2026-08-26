package dev.sandoretti.ukun.gateway;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOAuth2Client;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOAuth2Login;

import dev.sandoretti.ukun.gateway.support.AccessTokens;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The one question the edge decides, and the credential it decides it on: {@code /api/admin/**} is
 * granted from the access token this gateway is about to relay, never from the authorities the
 * session's principal captured at login (ADR-0020, issue #145).
 *
 * <h2>What each test pins</h2>
 *
 * The first two are the reproduction, and they are written as a pair on purpose: a session and its
 * relayed token are made to disagree in <em>both</em> directions, because a fix that only stopped one
 * of them would leave the other as a hole. The direction that was actually observed in the wild is
 * the second — an administrator refused by the gateway while {@code ukun-api} read {@code ADMIN} off
 * the very token the gateway had just relayed. The first is its mirror: an admin surface opened to a
 * session whose token no longer says so.
 *
 * <p>Both fail against the {@code hasRole("ADMIN")} this class replaced, which is the only way to
 * know they reproduce anything.
 *
 * <p>The remaining two are the statuses, and the difference between them is not cosmetic. A live
 * session whose token lacks the role gets 403, which means what it says. A session that can no longer
 * produce a token gets 401, because 403 would tell its owner "your account does not have this" — false,
 * and with no way out — while the SPA turns a 401 into {@code /signin?reason=expired}.
 *
 * <h2>The fixture, which is the interesting part</h2>
 *
 * Two mutators rather than one: {@code mockOAuth2Login} owns the principal, {@code mockOAuth2Client}
 * owns the authorized client behind it. Being able to set them apart <em>is</em> the fixture — a
 * single mutator that kept them in step could not express the defect at all.
 *
 * <p>The client mutator has to be applied <strong>first</strong>. {@code mockOAuth2Login} delegates
 * to {@code mockOAuth2Client} internally with an access token of its own, the literal string
 * {@code "access-token"}, and applying it last leaves that one in the session — where it does not
 * even parse as a JWT, so every case would answer 401 and two of these tests would pass for a reason
 * that has nothing to do with roles.
 *
 * <p>The tokens are signed for real ({@code AccessTokens}) rather than fed to a stubbed decoder. A
 * decoder replaced in the test context would be a second reading of the credential, which is the very
 * thing this class exists to rule out.
 */
class AdminAuthorizationIT extends FilterChainIntegrationTest {

    private static final String ADMIN_SURFACE = "/api/admin/products";

    /** The mirror of what was observed: the session still says ADMIN, the token no longer does. */
    @Test
    @DisplayName("refuses when the session says ADMIN and the relayed token does not")
    void refusesWhenOnlyTheSessionSaysAdmin() {
        session(List.of("ROLE_ADMIN"), AccessTokens.withoutRoles())
                .get().uri(ADMIN_SURFACE)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    /**
     * What issue #145 recorded: 403 at the gateway for an account that held the role, while
     * {@code GET /api/me} on the same session answered {@code roles: [EMPLOYEE, ADMIN]}.
     */
    @Test
    @DisplayName("lets through when the relayed token says ADMIN and the session does not")
    void letsThroughWhenTheRelayedTokenSaysAdmin() {
        session(List.of("ROLE_EMPLOYEE"), AccessTokens.withRoles(List.of("EMPLOYEE", "ADMIN")))
                .get().uri(ADMIN_SURFACE)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("refuses a token that carries no ADMIN, and calls it FORBIDDEN")
    void refusesATokenWithoutTheRole() {
        session(List.of("ROLE_CUSTOMER"), AccessTokens.withRoles(List.of("CUSTOMER")))
                .get().uri(ADMIN_SURFACE)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    /**
     * The session survived and its credential did not. Note the token says {@code ADMIN}: what denies
     * the request is that it cannot be validated, not what it claims — and the answer is 401, the same
     * one an anonymous caller gets, because neither is a permission problem and both are fixed by
     * signing in again.
     *
     * <p>Answering 403 here is the second half of issue #145: the SPA would paint "your account does
     * not have this" from the closed registry of {@code docs/12-error-contract.md}, which is false and
     * offers no way out, instead of sending the reader to {@code /signin?reason=expired}.
     */
    @Test
    @DisplayName("answers 401 when the session can no longer produce a usable token")
    void answersUnauthenticatedWhenTheSessionCannotAuthorise() {
        session(List.of("ROLE_ADMIN"), AccessTokens.signedByAnotherKey())
                .get().uri(ADMIN_SURFACE)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.traceId").isNotEmpty();
    }

    /**
     * The registration id is the mutator's own default rather than this service's {@code ukun}: what
     * matters is that the principal and the authorized client agree on one, which is what the
     * authorized client manager looks the token up by.
     */
    private WebTestClient session(List<String> granted, OAuth2AccessToken accessToken) {
        return http.mutateWith(mockOAuth2Client("test").accessToken(accessToken))
                .mutateWith(mockOAuth2Login().authorities(authorities(granted)));
    }

    private static List<GrantedAuthority> authorities(List<String> granted) {
        return granted.stream().<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
    }
}
