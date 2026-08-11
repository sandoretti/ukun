package dev.sandoretti.ukun.auth.security;

import dev.sandoretti.ukun.auth.application.AccountCredentials;
import dev.sandoretti.ukun.auth.application.AccountsService;
import dev.sandoretti.ukun.auth.domain.Role;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.event.Level;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The {@code auth.login.*} events of the closed registry in
 * {@code docs/11-observabilidad-y-logging.md} §3, emitted from the events Spring Security already
 * publishes.
 *
 * <p><strong>The choice of place is the whole correction, not the care taken while writing it.</strong>
 * The legacy logs a failed login from inside its controller, where the submitted password is in
 * scope, and duly leaks it — defect #10. A listener <em>cannot</em> make that mistake: the event
 * does not carry the password, so there is nothing to filter and nothing to remember
 * ({@code docs/11} §6).
 *
 * <p>The failure event distinguishes {@code CREDENTIALS} from {@code DISABLED} because those are the
 * two values the registry allows. Note what is deliberately <em>not</em> a third value: "no such
 * email". {@code DaoAuthenticationProvider} hides an unknown user behind
 * {@code BadCredentialsException}, so it lands here as {@code CREDENTIALS} — and that is the point,
 * because a log that could tell them apart would eventually be asked to, and the endpoint would
 * become an account enumeration oracle (docs/08 §8).
 */
@Component
class LoginEventListener {

    private final AccountsService accounts;

    LoginEventListener(AccountsService accounts) {
        this.accounts = accounts;
    }

    /**
     * The event is the <em>interactive</em> one, and that is the difference between one line per
     * login and two.
     *
     * <p>{@code AuthenticationSuccessEvent} is published by {@code ProviderManager} every time it
     * authenticates anything, and in this service a single login authenticates twice: once at
     * {@code POST /login}, and again when the Authorization Server authenticates the
     * {@code GET /oauth2/authorize} that follows it. Both carry the same principal, so both used to
     * emit an identical {@code auth.login.succeeded} — with a different {@code traceId} each,
     * because they are two requests, which is what made the pair look like two logins in the audit
     * trail rather than one logged twice.
     *
     * <p>{@code InteractiveAuthenticationSuccessEvent} is published only by
     * {@code AbstractAuthenticationProcessingFilter}, i.e. only where a person actually submitted
     * credentials. It is the event this registry entry has always meant.
     */
    @EventListener
    void onSuccess(InteractiveAuthenticationSuccessEvent event) {
        String email = event.getAuthentication().getName();

        accounts.findCredentialsByEmail(email)
                .ifPresent(account -> BusinessEvents.emit(
                        Level.INFO,
                        "auth.login.succeeded",
                        Map.of("accountId", account.accountId(), "roles", rolesOf(account))));
    }

    /**
     * WARN and not INFO, as the registry says: a failed login is not the normal course of events, and
     * a burst of them is the signal the level exists to make visible.
     */
    @EventListener
    void onFailure(AbstractAuthenticationFailureEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("email", normalise(event.getAuthentication().getName()));
        fields.put("reason", reasonOf(event));
        fields.put("ip", callerIp());

        BusinessEvents.emit(Level.WARN, "auth.login.failed", fields);
    }

    private static String reasonOf(AbstractAuthenticationFailureEvent event) {
        Exception cause = event.getException();
        return cause instanceof DisabledException || cause instanceof LockedException ? "DISABLED" : "CREDENTIALS";
    }

    /**
     * Lower-cased so that the same account does not scatter across log lines by how it was typed —
     * the column is {@code CITEXT} and the two spellings are one account. It is the email and not a
     * credential; {@code docs/11} §4 forbids the password, not the identifier of the attempt.
     */
    private static String normalise(String email) {
        return email == null ? "" : email.toLowerCase();
    }

    /**
     * Best effort: this runs on the request thread, but an authentication triggered outside one (a
     * token exchange, a test) has no request attributes and simply has no IP to report.
     *
     * <p>{@code getRemoteAddr()} and not {@code X-Forwarded-For}: the header is caller-supplied, and
     * trusting it here would let anyone write whatever address they liked into the audit trail. When
     * the gateway becomes the only entry point (v0.5) it is the gateway that owns forwarding it.
     */
    private static String callerIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }

    private static List<String> rolesOf(AccountCredentials account) {
        return account.roles().stream().map(Role::name).toList();
    }
}
