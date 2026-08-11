package dev.sandoretti.ukun.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.gateway.security.UkunOidcUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Who ends up named in the access log. The decision moved here when {@link AccessLogWebFilter} went
 * in front of the security chain: that filter now prints whatever this one deposited, so the rule
 * about <em>never</em> naming somebody by email is enforced at this end.
 */
class AccountIdAttributeWebFilterTest {

    private static final String CATALOGUE = "/api/products";

    private static final String ACCOUNT_ID = UUID.randomUUID().toString();

    /** What {@code getName()} returns for this principal, and what must never reach a log line. */
    private static final String EMAIL = "ada@example.com";

    private final AccountIdAttributeWebFilter filter = new AccountIdAttributeWebFilter();

    @Test
    @DisplayName("leaves the account id of a signed-in caller on the exchange")
    void depositsTheAccountIdOfASignedInCaller() {
        ServerWebExchange exchange = exchangeWith(loggedInUser());

        filter.filter(exchange, passThrough()).block();

        assertThat(exchange.getAttributes()).containsEntry(AccessLogAttributes.ACCOUNT_ID, ACCOUNT_ID);
    }

    /**
     * A principal of another shape — nothing in this service produces one today, but a future
     * authentication mechanism could — is treated as anonymous rather than named by whatever
     * {@code getName()} happens to return. Guessing is how the email would get in.
     */
    @Test
    @DisplayName("a principal that is not this gateway's user is not named")
    void doesNotGuessTheAccountOfAnUnknownPrincipal() {
        ServerWebExchange exchange = exchangeWith(new TestingAuthenticationToken(EMAIL, "n/a", "ROLE_CUSTOMER"));

        filter.filter(exchange, passThrough()).block();

        assertThat(exchange.getAttributes()).doesNotContainKey(AccessLogAttributes.ACCOUNT_ID);
    }

    /**
     * And the chain still runs. An anonymous request to the public catalogue is the common case, and
     * a filter that only continued when it had somebody to name would take the catalogue down.
     */
    @Test
    @DisplayName("an anonymous request carries no account and still goes through")
    void letsAnAnonymousRequestThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(CATALOGUE));

        filter.filter(exchange, passThrough()).block();

        assertThat(exchange.getAttributes()).doesNotContainKey(AccessLogAttributes.ACCOUNT_ID);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private static ServerWebExchange exchangeWith(Authentication principal) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(CATALOGUE))
                .mutate()
                .principal(Mono.just(principal))
                .build();
    }

    private static WebFilterChain passThrough() {
        return exchange -> {
            exchange.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        };
    }

    private static Authentication loggedInUser() {
        OidcIdToken idToken = OidcIdToken.withTokenValue("an-id-token")
                // The id token names the person by email in this service; the account id lives in the
                // access token and is carried separately. That mismatch is the point of the test.
                .subject(EMAIL)
                .build();

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        UkunOidcUser user = new UkunOidcUser(authorities, idToken, "sub", ACCOUNT_ID);

        return new OAuth2AuthenticationToken(user, authorities, "ukun");
    }
}
