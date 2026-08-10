package dev.sandoretti.ukun.auth;

import static dev.sandoretti.ukun.auth.AccountMother.CUSTOMER_EMAIL;
import static dev.sandoretti.ukun.auth.AccountMother.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * The page itself: that it is this service's own and not the one Spring Security generates, that it
 * says the same thing in both languages, and that it says nothing it should not.
 *
 * <p>The mechanics of logging in through it are {@link LoginIT}'s, and the fact that they still pass
 * unchanged is the real proof that swapping the page did not move the contract underneath —
 * {@link FormLogin} reads the CSRF token out of this very HTML.
 */
class LoginPageIT extends LoginIntegrationTest {

    private String openLoginPage(String uri) {
        return openLoginPage(uri, "es-ES");
    }

    /**
     * The language is stated on every request, never left to the machine running the suite. That is
     * not pedantry: with Boot's default {@code fallback-to-system-locale}, a locale served from the
     * base bundle falls through to the <em>system</em> locale, so this page answered in English
     * inside a container with {@code LANG=en_US} and in Spanish on a developer's laptop — the same
     * build, two languages, and no test able to tell.
     */
    private String openLoginPage(String uri, String acceptLanguage) {
        return http.get()
                .uri(uri)
                .accept(MediaType.TEXT_HTML)
                .header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }

    @Test
    @DisplayName("the login page is the branded one, not the generated one")
    void servesTheBrandedPage() {
        String page = openLoginPage("/login");

        // The stylesheet is what makes it the shop's page rather than a bare form; Spring's
        // generated page carries its own inline styles and no link at all.
        assertThat(page).contains("/css/login.css").contains("auth-card");
        assertThat(page).contains("Bienvenido de nuevo");
    }

    /**
     * The page has to be reachable with no session at all — it is the door. A custom
     * {@code loginPage(...)} stops being permitted automatically, which is the kind of thing that
     * only shows up as an infinite redirect in a browser.
     */
    @Test
    @DisplayName("the page and its stylesheet are readable without a session")
    void servesTheStylesheetAnonymously() {
        String stylesheet = http.get()
                .uri("/css/login.css")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(stylesheet).contains(".auth-card");
    }

    /**
     * Both credentials are typed by a person, so both need a label and the autocomplete tokens a
     * password manager reads — {@code docs/07-frontend-ux-ui.md} asks the same of every field in the
     * SPA, and this form is not exempt for living in another service.
     */
    @Test
    @DisplayName("both fields are labelled and offered to a password manager")
    void labelsBothFields() {
        String page = openLoginPage("/login");

        assertThat(page).contains("for=\"username\"").contains("autocomplete=\"username\"");
        assertThat(page).contains("for=\"password\"").contains("autocomplete=\"current-password\"");
    }

    /**
     * The same call the SPA's own switcher makes: "ES" is an abbreviation, and an accessible name of
     * two letters is not a name. {@code title} does not stand in for it — screen readers announce it
     * inconsistently at best.
     */
    @Test
    @DisplayName("each language link is named in full, not by its two visible letters")
    void namesEachLanguageInFull() {
        String page = openLoginPage("/login");

        assertThat(page).contains("visually-hidden\">Español").contains("visually-hidden\">Inglés");
        assertThat(page).contains("aria-hidden=\"true\">ES").contains("aria-hidden=\"true\">EN");
    }

    @Test
    @DisplayName("it renders in English when the reader asks for English")
    void rendersInEnglish() {
        String page = openLoginPage("/login?lang=en");

        assertThat(page).contains("Welcome back").doesNotContain("Bienvenido de nuevo");
        assertThat(page).contains("lang=\"en\"");
    }

    /**
     * The browser's language decides, and a language this shop does not speak lands on Spanish —
     * never on whatever the host that happens to be running the process was configured with.
     */
    @Test
    @DisplayName("the browser's language decides, and an unknown one falls back to Spanish")
    void negotiatesFromTheBrowser() {
        assertThat(openLoginPage("/login", "en-GB,en;q=0.9")).contains("Welcome back");
        assertThat(openLoginPage("/login", "es-ES,es;q=0.9")).contains("Bienvenido de nuevo");
        assertThat(openLoginPage("/login", "fr-FR,fr;q=0.9")).contains("Bienvenido de nuevo");
    }

    /**
     * Changing language is not an attempt to sign in, and the page must not say it was.
     *
     * <p>It very nearly did: Thymeleaf's link builder writes {@code error=} when a URL parameter
     * resolves to null instead of leaving it out, and {@code ?error=} reads exactly like
     * {@code ?error}. The page accused the reader of getting their password wrong before they had
     * typed anything — visible only by clicking, which is why the assertion is on the link itself.
     */
    @Test
    @DisplayName("switching language on a clean page does not invent a failed attempt")
    void doesNotCarryAnErrorThatNeverHappened() {
        String page = openLoginPage("/login");

        assertThat(page).contains("href=\"/login?lang=en\"").doesNotContain("error");

        String afterSwitching = openLoginPage("/login?lang=en");

        assertThat(afterSwitching).doesNotContain("role=\"alert\"");
    }

    /** The other half: a real failure survives the switch, so the reason does not vanish. */
    @Test
    @DisplayName("switching language after a failed attempt keeps the warning")
    void carriesARealErrorAcrossTheSwitch() {
        String page = openLoginPage("/login?error");

        assertThat(page).contains("href=\"/login?lang=en&amp;error\"");

        String afterSwitching = openLoginPage("/login?lang=en&error");

        assertThat(afterSwitching).contains("That email or password is not right");
    }

    /**
     * The assertion this class shares with {@link LoginIT}, one layer up: that suite proves the two
     * failures are answered with the same redirect, and this one proves the page they land on does
     * not undo it by being helpful.
     */
    @Test
    @DisplayName("a failed attempt says one thing, whichever credential was wrong")
    void tellsNothingApartOnFailure() {
        accounts().customer();

        String afterWrongPassword = pageAfterFailing(CUSTOMER_EMAIL, "not-the-password");
        String afterUnknownEmail = pageAfterFailing("nobody@ukun.dev", PASSWORD);

        // Byte for byte, once the CSRF token is masked — it is the one thing that legitimately
        // differs, because each attempt gets its own session. Comparing whole pages rather than
        // looking for a phrase is what makes this fail if somebody ever adds a helpful hint to one
        // of the two branches.
        assertThat(withoutCsrfToken(afterWrongPassword)).isEqualTo(withoutCsrfToken(afterUnknownEmail));
        assertThat(afterWrongPassword).contains("El correo o la contraseña no son correctos");
        // The words an enumeration oracle would leak. None of them belongs on this page.
        assertThat(afterWrongPassword)
                .doesNotContain("no existe")
                .doesNotContain("not found")
                .doesNotContain(CUSTOMER_EMAIL);
    }

    /** The per-session CSRF token, blanked so two pages of two sessions can be compared as one. */
    private static String withoutCsrfToken(String page) {
        return page.replaceAll("value=\"[^\"]+\"", "value=\"?\"");
    }

    /** Fails a login on purpose and follows the redirect to read the page it lands on. */
    private String pageAfterFailing(String email, String password) {
        LoginAttempt attempt = logIn(email, password);

        assertThat(attempt.succeeded()).isFalse();

        return http.get()
                .uri(attempt.location())
                .accept(MediaType.TEXT_HTML)
                .header(HttpHeaders.COOKIE, attempt.sessionCookie())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }
}
