package dev.sandoretti.ukun.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.ServerRequest;

/**
 * What the SPA fallback will and will not answer. It is a unit test on purpose: this predicate is
 * the guard that keeps a backend prefix from being turned into an HTML page, and a regression in it
 * should be visible without starting a server.
 *
 * <p>The {@code /internal/**} case is the one that matters most. Those endpoints belong to
 * {@code ukun-auth}'s signup saga and only {@code ukun-api} may call them, from inside the container
 * network ({@code docs/08-identidad-y-seguridad.md} §3). There is no route to them, so what decides
 * between a 404 and a friendly {@code index.html} is exactly this predicate.
 */
class SpaFallbackPredicateTest {

    private static final RequestPredicate FALLBACK = SpaFallbackConfig.spaFallback();

    @Test
    @DisplayName("answers a client-side route of the SPA")
    void answersAClientSideRoute() {
        assertThat(FALLBACK.test(navigationTo("/orders/42"))).isTrue();
    }

    @Test
    @DisplayName("refuses every reserved prefix")
    void refusesEveryReservedPrefix() {
        assertThat(FALLBACK.test(navigationTo("/api/products"))).isFalse();
        assertThat(FALLBACK.test(navigationTo("/media/products/sofa.jpg"))).isFalse();
        assertThat(FALLBACK.test(navigationTo("/oauth2/authorize"))).isFalse();
        assertThat(FALLBACK.test(navigationTo("/login/oauth2/code/ukun"))).isFalse();
        assertThat(FALLBACK.test(navigationTo("/.well-known/openid-configuration"))).isFalse();
        assertThat(FALLBACK.test(navigationTo("/actuator/health"))).isFalse();
        assertThat(FALLBACK.test(navigationTo("/internal/accounts"))).isFalse();
    }

    /** The prefix is a path segment, not a string prefix: {@code /apixel} is a page like any other. */
    @Test
    @DisplayName("answers a route that merely starts like a reserved prefix")
    void answersARouteThatMerelyStartsLikeAReservedPrefix() {
        assertThat(FALLBACK.test(navigationTo("/apixel"))).isTrue();
    }

    /**
     * A browser asks for scripts with {@code Accept: &#42;/&#42;}, which is compatible with
     * {@code text/html}, so the extension is what stops a missing asset from being answered with the
     * HTML shell — and reported as a syntax error rather than as the 404 it is.
     */
    @Test
    @DisplayName("refuses a missing asset instead of answering the HTML shell")
    void refusesAMissingAsset() {
        ServerRequest asset = request("/assets/app-abc123.js", MediaType.ALL);

        assertThat(FALLBACK.test(asset)).isFalse();
    }

    @Test
    @DisplayName("refuses a request that does not accept HTML")
    void refusesARequestThatDoesNotAcceptHtml() {
        ServerRequest json = request("/orders/42", MediaType.APPLICATION_JSON);

        assertThat(FALLBACK.test(json)).isFalse();
    }

    @Test
    @DisplayName("refuses anything that is not a GET or a HEAD")
    void refusesAnythingThatIsNotAGetOrAHead() {
        ServerRequest post = ServerRequest.create(
                MockServerWebExchange.from(MockServerHttpRequest.post("/orders")
                        .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)),
                HandlerStrategies.withDefaults().messageReaders());

        assertThat(FALLBACK.test(post)).isFalse();
    }

    private static ServerRequest navigationTo(String path) {
        return request(path, MediaType.TEXT_HTML);
    }

    private static ServerRequest request(String path, MediaType accept) {
        return ServerRequest.create(
                MockServerWebExchange.from(MockServerHttpRequest.get(path).accept(accept)),
                HandlerStrategies.withDefaults().messageReaders());
    }
}
