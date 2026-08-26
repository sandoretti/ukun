package dev.sandoretti.ukun.gateway.security;

import java.io.Serial;
import org.springframework.security.access.AccessDeniedException;

/**
 * The session is still there and can no longer authorise anything: the refresh was refused, or the
 * access token it holds does not validate any more.
 *
 * <h2>Why a denial that answers 401</h2>
 *
 * It is an {@link AccessDeniedException} because that is what a {@code ReactiveAuthorizationManager}
 * is allowed to raise — an {@code AuthenticationException} thrown this late is swallowed by
 * {@code AuthenticationWebFilter}, which resumes it into {@code oauth2Login}'s failure handler and
 * answers a 302 to {@code /login?error}, a page this system does not have. Measured, not assumed.
 *
 * <p>But the status it deserves is 401, not 403, and {@link ProblemDetailSecurityHandler} reads this
 * type to say so. 403 means "your account does not have this permission", which is what the frontend
 * paints from the closed registry of {@code docs/12-error-contract.md} — and for a dead session it is
 * both false and a dead end. 401 is the answer with a way out: {@code shared/api/http.ts} already
 * sends it to {@code /signin?reason=expired}. Issue #145.
 */
final class SessionCannotAuthoriseException extends AccessDeniedException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String MESSAGE = "The session no longer holds a usable access token";

    /** Nothing to read: no authorized client behind the principal, or no principal at all. */
    SessionCannotAuthoriseException() {
        super(MESSAGE);
    }

    /** Something to read that did not hold up: a refused refresh, or a token that does not validate. */
    SessionCannotAuthoriseException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
