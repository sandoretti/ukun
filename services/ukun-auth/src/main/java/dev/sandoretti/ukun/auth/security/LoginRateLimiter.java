package dev.sandoretti.ukun.auth.security;

import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stops a login attempt that has run out of budget before it reaches authentication at all
 * ({@code docs/08-identidad-y-seguridad.md} §8).
 *
 * <p>Deliberately not a {@code @Component}: Boot registers any {@code Filter} bean into the servlet
 * chain for every request, and this one belongs to a single route of a single security chain. It is
 * constructed by {@code AuthorizationServerConfig} and added with {@code addFilterBefore} — which is
 * the only reason this class and {@link LoginAttemptBuckets} are public rather than package-private:
 * the wiring lives in {@code config} and the component in {@code security}.
 *
 * <h2>Why the response is a bare 429 and not a ProblemDetail</h2>
 *
 * The closed registry of {@code docs/12-error-contract.md} has no code for rate limiting, and
 * inventing one is exactly what that registry exists to prevent — adding a code has its own process.
 * There is no need to: {@code /login} is a server-rendered form page of the Authorization Server, not
 * an operation of either OpenAPI document, so it is not governed by that contract in the first place
 * (the same reasoning that makes {@code /media/**} answer a bare 404). A {@code Retry-After} header
 * is what a standard client acts on here.
 *
 * <p>Worth a second's thought and then rejecting: answering the ordinary "bad credentials" page
 * would keep throttling indistinguishable from a wrong password. It would also leave a locked-out
 * person with no way to tell why nothing works, and the enumeration risk §8 guards against is about
 * <em>which emails exist</em> — which a 429 keyed on IP + email does not reveal, because an unknown
 * email is throttled exactly like a known one.
 */
public class LoginRateLimiter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/login";

    private static final String USERNAME_PARAMETER = "username";

    private final LoginAttemptBuckets buckets;

    public LoginRateLimiter(LoginAttemptBuckets buckets) {
        this.buckets = buckets;
    }

    /** Only the form submission. A GET of the login page consumes nothing. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && LOGIN_PATH.equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        ConsumptionProbe probe = buckets.forAttempt(request.getRemoteAddr(), request.getParameter(USERNAME_PARAMETER))
                .tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        long secondsToWait = Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds();

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, secondsToWait)));
    }
}
