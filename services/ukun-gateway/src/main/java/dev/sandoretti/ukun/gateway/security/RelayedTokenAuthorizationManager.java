package dev.sandoretti.ukun.gateway.security;

import java.util.List;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Decides the coarse rule of ADR-0020 on the access token this gateway is about to relay, rather than
 * on the authorities the session's principal captured at login.
 *
 * <h2>The defect this exists to make impossible (issue #145)</h2>
 *
 * {@link AccessTokenOidcUserService} runs once, inside the {@code authorization_code} exchange, and
 * never again — a {@code ReactiveOAuth2UserService} is not consulted when the token is refreshed. With
 * an access token that lives 15 minutes ({@code RegisteredClientSeeder} in {@code ukun-auth}) and a
 * refresh token that lives a week, a long session refreshes many times while authorities derived at
 * login stay the photograph they were. The two halves of the decision then had different lifetimes:
 *
 * <pre>
 *   gateway, hasRole("ADMIN")   authorities of the session principal   renewed only at login
 *   ukun-api, @PreAuthorize     claims of the relayed token            renewed on every refresh
 * </pre>
 *
 * <p>What was observed was the gateway answering 403 to {@code /api/admin/**} for an account that did
 * hold {@code ADMIN} — {@code GET /api/me} on the same session answering {@code roles: [EMPLOYEE,
 * ADMIN]}, read by {@code ukun-api} from the token this service had just relayed. Which of the two
 * suspected causes degraded that particular principal was never determined, and with this manager it
 * stops mattering: there is no derived copy left to fall behind.
 *
 * <p>This is what {@code docs/08-identidad-y-seguridad.md} §4 asks for in the first place. The API is
 * the authority; the edge is <em>a cheap, consistent echo of it, never a second opinion</em>. An echo
 * that reads a different credential than the one it forwards is not consistent — it is a second
 * opinion with a delay.
 *
 * <h2>Why it asks the authorized client manager and not the session</h2>
 *
 * It goes through the very {@link ReactiveOAuth2AuthorizedClientManager} that {@code TokenRelay} uses
 * a moment later, and builds the same {@link OAuth2AuthorizeRequest}. That is what makes "the token
 * this request will carry" literal rather than approximate: if the token needs refreshing, this call
 * refreshes it, and the relay then reads the refreshed one back from the session. Two calls, one
 * refresh, and only under {@code /api/admin/**}.
 *
 * <h2>401 and 403 say different things</h2>
 *
 * A session that can no longer produce a usable token has not lost a permission — it has stopped being
 * a session. Answering 403 tells the reader "your account does not have this", which is false and
 * offers no way out; the frontend paints exactly those words from the closed registry
 * ({@code docs/12-error-contract.md}). So that case raises {@link SessionCannotAuthoriseException},
 * which {@link ProblemDetailSecurityHandler} answers with the 401 {@code UNAUTHENTICATED} that
 * {@code shared/api/http.ts} already translates into {@code /signin?reason=expired}. A live session
 * whose token simply lacks the role keeps its 403.
 */
final class RelayedTokenAuthorizationManager implements ReactiveAuthorizationManager<AuthorizationContext> {

    /** {@code docs/08-identidad-y-seguridad.md} §4. Names as minted, with no {@code ROLE_} prefix. */
    private static final String ROLES_CLAIM = "roles";

    private final ReactiveOAuth2AuthorizedClientManager authorizedClients;
    private final ReactiveJwtDecoder accessTokens;
    private final String role;

    RelayedTokenAuthorizationManager(
            ReactiveOAuth2AuthorizedClientManager authorizedClients, ReactiveJwtDecoder accessTokens, String role) {

        this.authorizedClients = authorizedClients;
        this.accessTokens = accessTokens;
        this.role = role;
    }

    @Override
    public Mono<AuthorizationResult> authorize(Mono<Authentication> authentication, AuthorizationContext context) {
        return authentication
                .flatMap(caller -> relayedTokenOf(caller, context.getExchange()))
                .<AuthorizationResult>map(token -> new AuthorizationDecision(rolesOf(token).contains(role)))
                // Nobody signed in, and a session that cannot produce a token any more, are the same
                // answer on purpose: neither is a permission problem, and both are fixed by signing in.
                .switchIfEmpty(Mono.error(RelayedTokenAuthorizationManager::sessionCannotAuthorise));
    }

    /**
     * Empty rather than an error when there is nothing to read: an anonymous caller carries no
     * {@code OAuth2AuthenticationToken} at all, and a session whose refresh was refused carries one
     * with no authorized client behind it. Both land on the 401 above.
     */
    private Mono<Jwt> relayedTokenOf(Authentication caller, ServerWebExchange exchange) {
        if (!(caller instanceof OAuth2AuthenticationToken session)) {
            return Mono.empty();
        }

        return authorizedClients
                .authorize(OAuth2AuthorizeRequest.withClientRegistrationId(session.getAuthorizedClientRegistrationId())
                        .principal(session)
                        // The key TokenRelay uses too. Without it the manager cannot reach the
                        // request, and a refresh needing the exchange fails instead of happening.
                        .attribute(ServerWebExchange.class.getName(), exchange)
                        .build())
                .map(client -> client.getAccessToken().getTokenValue())
                .flatMap(accessTokens::decode)
                // A refusal from the authorization server and a token this service cannot validate are
                // the same situation from here: the session no longer holds a usable credential. Left
                // as errors they would surface as 500s, which is the one answer that is never true.
                .onErrorMap(OAuth2AuthorizationException.class, RelayedTokenAuthorizationManager::sessionCannotAuthorise)
                .onErrorMap(JwtException.class, RelayedTokenAuthorizationManager::sessionCannotAuthorise);
    }

    /** No claim means no roles, never an error: the decision above then simply denies. */
    private static List<String> rolesOf(Jwt accessToken) {
        List<String> roles = accessToken.getClaimAsStringList(ROLES_CLAIM);

        return roles == null ? List.of() : roles;
    }

    private static SessionCannotAuthoriseException sessionCannotAuthorise() {
        return new SessionCannotAuthoriseException();
    }

    private static SessionCannotAuthoriseException sessionCannotAuthorise(Throwable cause) {
        return new SessionCannotAuthoriseException(cause);
    }
}
