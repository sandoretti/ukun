package dev.sandoretti.ukun.gateway.security;

import dev.sandoretti.ukun.gateway.error.ProblemDetailWriter;
import dev.sandoretti.ukun.gateway.web.AccessLogAttributes;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Turns the two rejections of the coarse route filter into the {@code ProblemDetail} of
 * {@code docs/12-error-contract.md}, the same shape {@code ukun-api} answers with — see
 * {@code ProblemDetailAuthenticationHandler} there, which implements the servlet halves of these two
 * interfaces for exactly this reason.
 *
 * <h2>Why this is not optional</h2>
 *
 * The default entry point of {@code ExceptionTranslationWebFilter} is
 * {@code HttpBasicServerAuthenticationEntryPoint}: a 401 with {@code WWW-Authenticate: Basic}, which
 * a browser answers by opening its native credentials dialog. On a BFF whose whole login flow is a
 * redirect to the authorization server, that dialog is not a cosmetic problem — it is a dead end the
 * user cannot get out of. And the body would be empty, so the SPA would have no {@code code} to
 * switch on.
 *
 * <p>One class for both interfaces, because the two responses differ only in which code they carry
 * and a reader looking for "what does the edge answer when it says no" should find one file.
 */
@Component
class ProblemDetailSecurityHandler implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private final ProblemDetailWriter problems;

    ProblemDetailSecurityHandler(ProblemDetailWriter problems) {
        this.problems = problems;
    }

    /** No session. The SPA reads the {@code code} and decides whether to start a login itself. */
    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        return problems.unauthenticated(exchange);
    }

    /**
     * A refusal from the authorisation half of the chain, which is two different things wearing one
     * exception type.
     *
     * <p>The usual one is a caller whose relayed token does not carry {@code ADMIN} on
     * {@code /api/admin/**}, or a mutation without its CSRF token: those are 403, and they mean what
     * 403 means. {@link SessionCannotAuthoriseException} is the other, and it is a <em>401</em> — a
     * session that can no longer produce a usable token has not lost a permission, it has stopped
     * being a session, and telling its owner "your account does not have this" is both false and a
     * dead end (issue #145).
     *
     * <p>It names the caller on the exchange before answering, and that is the only place it can be
     * done: this is a request that <em>had</em> a principal and still stopped inside the security
     * chain, so {@code AccountIdAttributeWebFilter} below never runs and the access log — which sits
     * in front of the chain — would otherwise report the one denial that has somebody behind it as
     * anonymous. A refusal whose {@code accountId} is missing is exactly the line an audit would want.
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException exception) {
        return exchange.getPrincipal()
                .map(UkunOidcUser::accountIdOf)
                .doOnNext(accountId -> accountId.ifPresent(
                        id -> exchange.getAttributes().put(AccessLogAttributes.ACCOUNT_ID, id)))
                .then(exception instanceof SessionCannotAuthoriseException
                        ? problems.unauthenticated(exchange)
                        : problems.forbidden(exchange));
    }
}
