package dev.sandoretti.ukun.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.gateway.error.ProblemDetailWriter;
import dev.sandoretti.ukun.gateway.web.AccessLogAttributes;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * The half of the denial that the access log cannot see for itself.
 *
 * <p>A request refused for lacking a role stops inside the security chain, so the filter that normally
 * puts the caller's id on the exchange never runs — and the access log, which sits in front of the
 * chain precisely so that refusals get a line, would report the one denial that <em>has</em> somebody
 * behind it as anonymous. This handler is the last place that still knows who it was.
 */
class ProblemDetailSecurityHandlerTest {

    private static final String ACCOUNT_ID = UUID.randomUUID().toString();

    /** {@code getName()} of this principal, and what must never be what names somebody in a log. */
    private static final String EMAIL = "ada@example.com";

    private final ProblemDetailSecurityHandler handler =
            new ProblemDetailSecurityHandler(new ProblemDetailWriter(new ObjectMapper()));

    @Test
    @DisplayName("names the account behind a request denied for lacking a role")
    void depositsTheAccountOfAForbiddenCaller() {
        ServerWebExchange exchange = exchangeWith(loggedInUser());

        handler.handle(exchange, new AccessDeniedException("no")).block();

        assertThat(exchange.getAttributes()).containsEntry(AccessLogAttributes.ACCOUNT_ID, ACCOUNT_ID);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * The response is written whether or not there was anybody to name. An implementation that only
     * answered when it found a principal would turn a 403 into a request that never completes.
     */
    @Test
    @DisplayName("still answers when there is no principal to name")
    void answersWithoutAPrincipal() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/admin/products"));

        handler.handle(exchange, new AccessDeniedException("no")).block();

        assertThat(exchange.getAttributes()).doesNotContainKey(AccessLogAttributes.ACCOUNT_ID);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("an unauthenticated caller is answered without inventing an account")
    void commencesWithoutAnAccount() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/admin/products"));

        handler.commence(exchange, new org.springframework.security.authentication.BadCredentialsException("no"))
                .block();

        assertThat(exchange.getAttributes()).doesNotContainKey(AccessLogAttributes.ACCOUNT_ID);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static ServerWebExchange exchangeWith(Authentication principal) {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/admin/products"))
                .mutate()
                .principal(Mono.just(principal))
                .build();
    }

    private static Authentication loggedInUser() {
        OidcIdToken idToken =
                OidcIdToken.withTokenValue("an-id-token").subject(EMAIL).build();

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        UkunOidcUser user = new UkunOidcUser(authorities, idToken, "sub", ACCOUNT_ID);

        return new OAuth2AuthenticationToken(user, authorities, "ukun");
    }
}
