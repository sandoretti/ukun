package dev.sandoretti.ukun.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

/**
 * What the login page needs from Spring MVC, which is only its language.
 *
 * <p>The shop is bilingual through react-i18next (ADR-0019) and this page cannot use any of it: it
 * is rendered here, before React exists in the browser and on a different origin from the one that
 * holds the reader's chosen language. So it negotiates its own, from the two supported languages,
 * and offers a switch of its own — the same two captions the navigation bar shows.
 *
 * <p><strong>Known gap:</strong> a reader who picked English inside the shop still arrives here in
 * whatever their browser asks for. Carrying the choice across means sending {@code ui_locales} on
 * the authorization request, which is a change in the gateway's request resolver rather than here.
 * The switch on the page is what makes that gap recoverable in one click instead of a dead end.
 */
@Configuration(proxyBeanMethods = false)
class WebMvcConfig implements WebMvcConfigurer {

    /** The same two as {@code apps/ukun-web/src/i18n/languages.ts}, and in the same order. */
    private static final List<String> SUPPORTED = List.of("es", "en");

    private static final Locale FALLBACK = Locale.of("es");

    /** The query parameter the ES|EN links of the page use. */
    static final String LANGUAGE_PARAM = "lang";

    /**
     * The language lives in the session, so the choice survives a failed login attempt — the form
     * posts and comes back to {@code /login?error}, and a page that reverted to the browser's
     * language exactly when someone mistypes their password would be its own small insult.
     *
     * <p>The session is already there: it is where {@code /oauth2/authorize} saved the request that
     * sent this browser to the login page in the first place.
     */
    @Bean
    LocaleResolver localeResolver() {
        SessionLocaleResolver resolver = new SessionLocaleResolver();
        // A function and not an overridden `determineDefaultLocale`: Spring Framework 7 dropped that
        // protected hook, and an example written against 6.x fails to compile rather than misbehave.
        resolver.setDefaultLocaleFunction(WebMvcConfig::negotiate);

        return resolver;
    }

    /** Until something is chosen, what the browser asks for — if we speak it. */
    private static Locale negotiate(HttpServletRequest request) {
        Locale requested = request.getLocale();

        return requested != null && SUPPORTED.contains(requested.getLanguage())
                ? Locale.of(requested.getLanguage())
                : FALLBACK;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor languageSwitch = new LocaleChangeInterceptor();
        languageSwitch.setParamName(LANGUAGE_PARAM);
        // An unknown or malformed value leaves the language alone instead of throwing a 500 at
        // someone whose only crime was editing the address bar.
        languageSwitch.setIgnoreInvalidLocale(true);

        registry.addInterceptor(languageSwitch);
    }
}
