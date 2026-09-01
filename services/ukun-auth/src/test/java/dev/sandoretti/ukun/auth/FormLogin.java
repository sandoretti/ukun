package dev.sandoretti.ukun.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Logging in the way a browser does it: fetch the page for its CSRF token and session cookie, then
 * post the credentials back with both.
 *
 * <p>It is a collaborator and not a method of {@link LoginIntegrationTest} because two fixtures need
 * it and they cannot share a base class: that one pins {@code contexts=prod}, its own database and a
 * truncation after every test, while {@link DemoAccountsIT} needs the opposite of all three — the
 * seeded database, the {@code demo} context, and rows that survive.
 *
 * <p>It would be shorter to authenticate with a test post-processor and skip the page entirely.
 * Doing it the long way is what makes these tests able to fail for the reasons that matter: the login
 * page exists and is reachable, CSRF is on and its token round-trips, and the session cookie is
 * issued.
 */
final class FormLogin {

    /** The default form login lands here on success and on {@code /login?error} on failure. */
    static final String LOGIN_PATH = "/login";

    /**
     * Still {@code JSESSIONID} after v1.0 moved the session into {@code ukun_auth} (ADR-0024), and
     * that is pinned in {@code application.yaml} rather than left at Spring Session's default of
     * {@code SESSION} — which is the name the gateway's own session already uses. Cookies do not
     * distinguish port, so on {@code localhost} the two would be one cookie overwriting the other,
     * and the visible symptom is not a session error but a login that never completes.
     */
    private static final String SESSION_COOKIE = "JSESSIONID=";

    private static final Pattern CSRF_TOKEN =
            Pattern.compile("name=\"_csrf\".*?value=\"([^\"]+)\"", Pattern.DOTALL);

    private final RestTestClient http;

    FormLogin(RestTestClient http) {
        this.http = http;
    }

    /**
     * @return the response of the POST, whose {@code Location} says whether it worked and whose
     *     {@code Set-Cookie} carries the authenticated session
     */
    LoginAttempt logIn(String email, String password) {
        LoginPage page = openLoginPage();

        var result = http.post()
                .uri(LOGIN_PATH)
                .header(HttpHeaders.COOKIE, page.sessionCookie())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("username=%s&password=%s&_csrf=%s".formatted(encode(email), encode(password), page.csrfToken()))
                .exchange()
                .returnResult();

        HttpHeaders headers = result.getResponseHeaders();
        String session = firstSessionCookie(headers);

        return new LoginAttempt(
                result.getStatus().value(),
                headers.getFirst(HttpHeaders.LOCATION),
                headers.getFirst(HttpHeaders.RETRY_AFTER),
                session == null ? page.sessionCookie() : session);
    }

    private LoginPage openLoginPage() {
        var result = http.get().uri(LOGIN_PATH).exchange().expectStatus().isOk().expectBody(String.class)
                .returnResult();

        Matcher matcher = CSRF_TOKEN.matcher(result.getResponseBody());
        if (!matcher.find()) {
            throw new IllegalStateException("The login page carries no CSRF token; is CSRF disabled by accident?");
        }

        return new LoginPage(matcher.group(1), firstSessionCookie(result.getResponseHeaders()));
    }

    /**
     * Only the {@code name=value} pair, without the attributes: it is what goes back in a
     * {@code Cookie} header, and Spring Security rotates the session id on login, so the value read
     * from the login response is not the one read from the page.
     */
    private static String firstSessionCookie(HttpHeaders headers) {
        return headers.getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                .filter(cookie -> cookie.startsWith(SESSION_COOKIE))
                .map(cookie -> cookie.split(";", 2)[0])
                .findFirst()
                .orElse(null);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** The two things a browser takes away from the login page before it can post credentials back. */
    private record LoginPage(String csrfToken, String sessionCookie) {}
}
