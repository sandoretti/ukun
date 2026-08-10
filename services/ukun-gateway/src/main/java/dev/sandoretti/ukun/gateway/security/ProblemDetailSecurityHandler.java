package dev.sandoretti.ukun.gateway.security;

import dev.sandoretti.ukun.gateway.error.ProblemDetailWriter;
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

    /** No credentials at all. Today that is everyone: the login flow arrives with issue #28. */
    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
        return problems.unauthenticated(exchange);
    }

    /** Authenticated, but not {@code ROLE_ADMIN} on {@code /api/admin/**}. */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException exception) {
        return problems.forbidden(exchange);
    }
}
