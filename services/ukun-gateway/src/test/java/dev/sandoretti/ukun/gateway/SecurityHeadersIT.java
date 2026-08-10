package dev.sandoretti.ukun.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

/**
 * The four headers of {@code docs/08-identidad-y-seguridad.md} §8 on every response — the direct
 * replacement for the legacy nginx, which emitted none of them.
 *
 * <p>Four responses and not one, because they are produced along four different paths and only the
 * first is obvious: a proxied 200 is written by the routing filter, the SPA shell by a handler of
 * this service, the 413 by a {@code WebFilter}, and a 404 by the dispatcher with no handler at all.
 * A change that keeps the headers on one of those and loses them on another is exactly the kind of
 * regression this pins down.
 *
 * <p>The {@code Strict-Transport-Security} assertion is the load-bearing one. Spring Security's own
 * writer stays silent unless the request scheme is {@code https}, so this fails the moment somebody
 * "simplifies" {@code GatewaySecurityConfig} back to the stock {@code hsts(...)} — and it would fail
 * in exactly the environment where nobody looks, because everything local is plain HTTP.
 */
class SecurityHeadersIT extends GatewayIntegrationTest {

    private static final byte[] TOO_LARGE = new byte[6 * 1024 * 1024];

    private static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    private static final String STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    private static final String CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String REFERRER_POLICY = "Referrer-Policy";

    @Test
    @DisplayName("adds the four security headers to a proxied response")
    void addsTheFourHeadersToAProxiedResponse() {
        assertHasTheFourHeaders(http.get().uri("/api/products").exchange());
    }

    @Test
    @DisplayName("adds the four security headers to the SPA shell")
    void addsTheFourHeadersToTheSpaShell() {
        assertHasTheFourHeaders(http.get().uri("/orders/42").accept(MediaType.TEXT_HTML).exchange());
    }

    /** The whole reason the size filter runs after the security chain — see {@code PayloadLimitIT}. */
    @Test
    @DisplayName("adds the four security headers to a body rejected for its size")
    void addsTheFourHeadersToABodyRejectedForItsSize() {
        assertHasTheFourHeaders(http.post().uri("/api/signup").bodyValue(TOO_LARGE).exchange());
    }

    @Test
    @DisplayName("adds the four security headers to a path that matches nothing")
    void addsTheFourHeadersToAPathThatMatchesNothing() {
        assertHasTheFourHeaders(http.get().uri("/internal/accounts").exchange());
    }

    /**
     * The half of the policy that actually stops an injected script. {@code style-src} carries
     * {@code 'unsafe-inline'} because react-bootstrap positions dropdowns and modals by writing the
     * {@code style} attribute through Popper ({@code docs/08} §8); {@code script-src} must not, and
     * the {@code dev} profile relaxing it is not a licence to relax it here.
     */
    @Test
    @DisplayName("does not allow inline scripts outside the dev profile")
    void doesNotAllowInlineScripts() {
        String policy = headersOf(http.get().uri("/api/products").exchange()).getFirst(CONTENT_SECURITY_POLICY);

        assertThat(directive(policy, "script-src")).isEqualTo("'self'");
    }

    private static void assertHasTheFourHeaders(ResponseSpec response) {
        HttpHeaders headers = headersOf(response);

        assertSoftly(softly -> {
            softly.assertThat(headers.getFirst(CONTENT_SECURITY_POLICY))
                    .as(CONTENT_SECURITY_POLICY)
                    .startsWith("default-src 'self';");
            softly.assertThat(headers.getFirst(STRICT_TRANSPORT_SECURITY))
                    .as(STRICT_TRANSPORT_SECURITY)
                    .isEqualTo("max-age=31536000 ; includeSubDomains");
            softly.assertThat(headers.getFirst(CONTENT_TYPE_OPTIONS))
                    .as(CONTENT_TYPE_OPTIONS)
                    .isEqualTo("nosniff");
            softly.assertThat(headers.getFirst(REFERRER_POLICY))
                    .as(REFERRER_POLICY)
                    .isEqualTo("strict-origin-when-cross-origin");
        });
    }

    private static HttpHeaders headersOf(ResponseSpec response) {
        return response.returnResult(Void.class).getResponseHeaders();
    }

    private static String directive(String policy, String name) {
        return Arrays.stream(policy.split(";"))
                .map(String::trim)
                .filter(directive -> directive.startsWith(name + " "))
                .map(directive -> directive.substring(name.length() + 1))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the policy declares no %s directive".formatted(name)));
    }
}
