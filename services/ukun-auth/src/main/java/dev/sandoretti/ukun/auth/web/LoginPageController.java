package dev.sandoretti.ukun.auth.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The only server-rendered page of the new system: the login form.
 *
 * <p>It is not a return of the Thymeleaf the legacy monoliths were built on. The frontend is still
 * React against a REST API (ADR-0002) and stays so; this one screen is here because the password is
 * typed at the Authorization Server and never at the SPA (ADR-0003), which puts it on this origin
 * and outside React's reach. Its look is kept in step with the shop by hand — see
 * {@code static/css/login.css} and the parity test that guards the palette.
 *
 * <p>Spring Security owns the two flags this reads: it redirects here with {@code ?error} after a
 * failed attempt and {@code ?logout} after signing out. They are turned into model attributes rather
 * than read from {@code param} in the template because <strong>Thymeleaf forbids touching
 * {@code param} inside a link expression</strong> — deliberately, so that a request parameter cannot
 * be woven into a URL the page emits. The language links need exactly that decision, so it is taken
 * here, where it is also plain to read.
 */
@Controller
class LoginPageController {

    private static final String LOGIN_PATH = "/login";

    private final String storeUrl;

    LoginPageController(@Value("${ukun.store.url}") String storeUrl) {
        this.storeUrl = storeUrl;
    }

    /**
     * The page named by {@code formLogin().loginPage(...)}. It is a plain view: no branch on whether
     * someone is already authenticated, because Spring Security's own filter has already decided
     * that by the time a request gets here.
     *
     * <p>Both parameters are flags with no value — {@code ?error}, not {@code ?error=something} — so
     * what matters about them is only whether they arrived.
     */
    @GetMapping(LOGIN_PATH)
    String login(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            Model model) {

        boolean failed = error != null;

        model.addAttribute("storeUrl", storeUrl);
        model.addAttribute("failed", failed);
        model.addAttribute("signedOut", logout != null);
        // Changing language keeps a real failure —losing the warning mid-read would be its own small
        // rudeness— and never invents one, which is what happens if the parameter is emitted with an
        // empty value: `?error=` reads exactly like `?error`.
        model.addAttribute("spanishHref", languageHref("es", failed));
        model.addAttribute("englishHref", languageHref("en", failed));

        return "login";
    }

    private static String languageHref(String language, boolean failed) {
        return LOGIN_PATH + "?lang=" + language + (failed ? "&error" : "");
    }
}
