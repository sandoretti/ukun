package dev.sandoretti.ukun.auth.oauth2;

import dev.sandoretti.ukun.auth.AbstractIntegrationTest;
import dev.sandoretti.ukun.auth.accounts.AccountMother;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The fixture the three login suites share: a running Authorization Server, a real Postgres, and the
 * pieces needed to drive a form login over HTTP the way a browser does.
 *
 * <p>The Spring annotations live here rather than on each subclass on purpose — the context cache key
 * includes the {@code @DynamicPropertySource} methods, so three subclasses declaring their own would
 * start three application contexts and run Liquibase three times for one identical configuration.
 *
 * <h2>Why the login is driven through the real HTML page</h2>
 *
 * It would be shorter to authenticate with a test post-processor and skip the page. Doing it the
 * long way is what makes these tests able to fail for the reasons that matter: the login page exists
 * and is reachable, CSRF is on and its token round-trips, the session cookie is issued, and the rate
 * limiter sits in front of the authentication filter rather than somewhere that never runs.
 */
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.liquibase.contexts=prod",
            "ukun.oauth2.gateway.secret=" + LoginIntegrationTest.GATEWAY_SECRET,
            "ukun.oauth2.api.secret=" + LoginIntegrationTest.API_SECRET
        })
abstract class LoginIntegrationTest extends AbstractIntegrationTest {

    static final String GATEWAY_SECRET = "test-gateway-secret";
    static final String API_SECRET = "test-api-secret";

    /** Must match what the seeder registers, or the authorization request is rejected. */
    static final String GATEWAY_REDIRECT_URI = "http://localhost:8080/login/oauth2/code/ukun";

    /** The default form login lands here on success and on {@code /login?error} on failure. */
    static final String LOGIN_PATH = "/login";

    private static final Pattern CSRF_TOKEN =
            Pattern.compile("name=\"_csrf\".*?value=\"([^\"]+)\"", Pattern.DOTALL);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, ACCOUNTS_DATABASE);
    }

    @Autowired
    protected RestTestClient http;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    protected AccountMother accounts() {
        return new AccountMother(jdbc, passwordEncoder);
    }

    /**
     * A form login attempt, carried out the way a browser does it: fetch the page for its CSRF token
     * and session cookie, then post the credentials back with both.
     *
     * @return the response of the POST, whose {@code Location} says whether it worked and whose
     *     {@code Set-Cookie} carries the authenticated session
     */
    protected LoginAttempt logIn(String email, String password) {
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
                .filter(cookie -> cookie.startsWith("JSESSIONID="))
                .map(cookie -> cookie.split(";", 2)[0])
                .findFirst()
                .orElse(null);
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Ordered truncation rather than {@code @DirtiesContext}, per the `testing` skill. */
    @AfterEach
    void clearAccounts() {
        jdbc.execute("TRUNCATE TABLE account, oauth2_authorization RESTART IDENTITY CASCADE");
    }

    protected record LoginPage(String csrfToken, String sessionCookie) {}

    protected record LoginAttempt(int status, String location, String retryAfter, String sessionCookie) {

        boolean succeeded() {
            return location != null && !location.contains("error");
        }
    }
}
