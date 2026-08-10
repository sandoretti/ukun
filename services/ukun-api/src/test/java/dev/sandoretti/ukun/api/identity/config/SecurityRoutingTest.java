package dev.sandoretti.ukun.api.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import dev.sandoretti.ukun.api.identity.ProbeSecuredEndpoints;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Which routes need a token and which do not, and what a refusal looks like. The routing half of
 * issue #24; whether a <em>real</em> token is accepted — issuer, audience, signature — is
 * {@link ResourceServerIT}'s subject, against a real JWKS.
 *
 * <p>A slice rather than a full context: the decision under test is made by the filter chain and the
 * advice, and neither needs a database. The {@code JwtDecoder} is mocked because
 * {@code SecurityMockMvcRequestPostProcessors.jwt()} builds an already-validated authentication and
 * never calls it — the bean only has to exist for {@code oauth2ResourceServer().jwt()} to wire up.
 *
 * <p>Authorities are stated by the test rather than derived from the {@code roles} claim: the
 * converter that does that derivation is configured by properties and lives in the full context. That
 * split is deliberate — this class asks "given these authorities, is the route open?", and the IT
 * asks "does this token produce those authorities?".
 *
 * <p>It lives beside {@link SecurityConfig} rather than with the rest of the identity tests because
 * that configuration is package-private, and it should stay that way: nothing outside this package has
 * any business importing the filter chain. The {@code @RestControllerAdvice} needs no import at all —
 * a {@code @WebMvcTest} slice loads every advice by definition, which is precisely why the shape of
 * the 403 can be asserted here.
 *
 * <p>Only the probe controller is loaded, so a public route answers {@code 404} here rather than
 * {@code 200}: there is no handler behind it. That is enough for what is being asserted, because the
 * filter chain runs <em>before</em> handler mapping — the difference between "let through" and
 * "rejected" is {@code 404} against {@code 401}, and the real {@code 200} is in the IT.
 */
@WebMvcTest(controllers = ProbeSecuredEndpoints.ProbeController.class)
@Import({SecurityConfig.class, ProbeSecuredEndpoints.class})
class SecurityRoutingTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("the public catalogue is never turned away for lack of a token")
    void shouldLeaveThePublicContractOpen() {
        assertThat(mvc.get().uri("/api/products")).hasStatus(HttpStatus.NOT_FOUND);
    }

    /**
     * The one that would have broken v0.3 in silence, in the other direction: {@code /api/stores} is
     * public and {@code /api/stores/{id}/stock} is not, so the pattern is exact and not
     * {@code /api/stores/**}. A single character there would hand every employee's stock to anonymous
     * callers.
     */
    @Test
    @DisplayName("a store's stock is not public just because the store list is")
    void shouldNotOpenStockAlongWithTheStoreList() {
        assertThat(mvc.get().uri("/api/stores/1/stock")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("a protected route without a token answers 401 in the contract's shape")
    void shouldRejectAnAnonymousCallerWithProblemDetail() {
        MvcTestResult result = mvc.get().uri(ProbeSecuredEndpoints.AUTHENTICATED).exchange();

        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
        assertThat(result).bodyJson().extractingPath("$.traceId").asString().isNotBlank();
    }

    @Test
    @DisplayName("a token is enough for a route that only requires being someone")
    void shouldAcceptAnyAuthenticatedCaller() {
        assertThat(mvc.get().uri(ProbeSecuredEndpoints.AUTHENTICATED).with(jwt())).hasStatus(HttpStatus.OK);
    }

    /**
     * This 403 arrives through {@code @PreAuthorize}, so it is raised inside the dispatcher and handled
     * by the advice — the path that answered {@code 500 INTERNAL_ERROR} until the advice learned to
     * catch {@code AccessDeniedException} instead of letting its catch-all swallow it.
     */
    @Test
    @DisplayName("a customer reaching an admin route answers 403, not 500")
    void shouldRejectAnInsufficientRoleWithProblemDetail() {
        MvcTestResult result = mvc.get()
                .uri(ProbeSecuredEndpoints.ADMIN_ONLY)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("the admin route opens for an admin")
    void shouldAcceptTheRequiredRole() {
        assertThat(mvc.get()
                        .uri(ProbeSecuredEndpoints.ADMIN_ONLY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .hasStatus(HttpStatus.OK);
    }
}
