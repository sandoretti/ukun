package dev.sandoretti.ukun.gateway;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import dev.sandoretti.ukun.gateway.support.UpstreamStub;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The coarse route filter of ADR-0020: one rule for {@code /api/admin/**}, one for
 * {@code /api/me/**}, and everything else public at this layer.
 *
 * <p>It is <strong>defence in depth and never the only barrier</strong> — {@code ukun-api} validates
 * the JWT itself and repeats every check with {@code @PreAuthorize} ({@code docs/08} §4). What is
 * verified here is only that the edge refuses what it can refuse cheaply, and that it refuses it in
 * the shape of the contract.
 *
 * <p>The roles come from the mutator rather than from a real login, and they still should: what this
 * class is about is the rule, not how a principal acquires its authorities. Where those authorities
 * come from in production — the {@code roles} claim of the relayed access token — is
 * {@code AccessTokenOidcUserServiceTest}, and the flow that produces it is {@code OAuth2LoginIT}.
 */
class RouteAuthorizationIT extends FilterChainIntegrationTest {

    /**
     * A 401 in the shape of the contract, and without {@code WWW-Authenticate}. The default entry
     * point would send {@code Basic}, which a browser answers by opening its native credentials
     * dialog — a dead end on a BFF whose login is a redirect (ADR-0003).
     */
    @Test
    @DisplayName("refuses the admin surface to an anonymous caller")
    void refusesTheAdminSurfaceToAnAnonymousCaller() {
        http.get().uri("/api/admin/products")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().doesNotExist("WWW-Authenticate")
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHENTICATED")
                .jsonPath("$.traceId").isNotEmpty();
    }

    @Test
    @DisplayName("refuses the admin surface to a customer")
    void refusesTheAdminSurfaceToACustomer() {
        http.mutateWith(mockUser().roles("CUSTOMER"))
                .get().uri("/api/admin/products")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("lets an admin through to ukun-api")
    void letsAnAdminThrough() {
        http.mutateWith(mockUser().roles("ADMIN"))
                .get().uri("/api/admin/products")
                .exchange()
                .expectStatus().isOk()
                .expectBody().json(UpstreamStub.API.echoOf("/api/admin/products"));
    }

    @Test
    @DisplayName("requires a session for the caller's own resources")
    void requiresASessionForOwnResources() {
        http.get().uri("/api/me/profile")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    /**
     * The public half of the rule, and the reason it is a rule and not a list: the catalogue and the
     * product images are reachable with no credentials at all, and nothing at this layer needs to
     * enumerate them.
     */
    @Test
    @DisplayName("leaves the catalogue and the product images public")
    void leavesTheCatalogueAndImagesPublic() {
        http.get().uri("/api/products")
                .exchange()
                .expectStatus().isOk();

        http.get().uri("/media/products/leather-sofa.jpg")
                .exchange()
                .expectStatus().isOk();
    }
}
