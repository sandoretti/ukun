package dev.sandoretti.ukun.api.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts {@code accountId} in the MDC for the duration of an authenticated request, so every line this
 * service logs while serving it says <em>who</em> it was serving
 * ({@code docs/11-observabilidad-y-logging.md} §5).
 *
 * <p>It is the second half of correlation, next to the {@code traceId} Micrometer Tracing puts there
 * on its own: the trace answers "which request was this", the account answers "whose". Together they
 * are also what joins a log line to a row of {@code audit_log} when that table arrives in v0.7
 * (ADR-0014).
 *
 * <h2>Why only {@code accountId}</h2>
 *
 * {@code docs/11} §5 is a closed list, and the exclusion that matters is {@code email}: it is PII and
 * it would appear on every line of every request, including the ones that have no business knowing
 * it. It shows up in exactly three business events, where it is the subject. {@code customerId} and
 * {@code employeeId} belong here too once something has resolved them — nothing has yet, and
 * inventing a lookup to fill the MDC would be a query per request for a log field.
 *
 * <p>{@code sub} is read as text and never parsed. This class logs; it does not authorise. Rejecting
 * a token whose subject is not a UUID is {@code JwtIdentityApi}'s decision, and duplicating it here
 * would mean a malformed token failed in a filter that only exists to name things.
 *
 * <h2>The {@code finally} is the whole point</h2>
 *
 * Tomcat pools its threads. A key left behind on one of them reattaches itself to the next request
 * that thread serves, under a different person — and a log that confidently attributes one user's
 * request to another is a correlation bug that reads exactly like a security bug. Hours get spent
 * ruling that out. Hence the {@code try/finally}, and hence {@code AccountIdMdcFilterTest}, which
 * asserts the removal rather than the insertion.
 */
public final class AccountIdMdcFilter extends OncePerRequestFilter {

    /** Same key the audit trail will use, and the one {@code docs/11} §5 names. */
    static final String ACCOUNT_ID = "accountId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String accountId = currentAccountId();
        if (accountId == null) {
            // Anonymous traffic is most of the catalogue, and it is not an error: it simply has no
            // account to name. Nothing is put, so nothing has to be removed.
            chain.doFilter(request, response);
            return;
        }

        MDC.put(ACCOUNT_ID, accountId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(ACCOUNT_ID);
        }
    }

    private static String currentAccountId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        return authentication instanceof JwtAuthenticationToken token
                ? token.getToken().getSubject()
                : null;
    }
}
