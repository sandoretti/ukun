package dev.sandoretti.ukun.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import dev.sandoretti.ukun.gateway.error.ProblemDetailWriter;
import dev.sandoretti.ukun.gateway.support.GatewayProperties;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Which requests are charged to which budget, without a server. Running out of one is cheap to
 * arrange here and expensive over a socket, so the arithmetic lives in this class and
 * {@code RateLimitIT} is left with the one thing only a real port can show: that the 429 comes out
 * of the wire with the shape and the headers the contract requires.
 *
 * <p>The mapper carries Spring's {@code ProblemDetail} mixin for the same reason
 * {@link RequestSizeLimitWebFilterTest} explains: without it the extensions nest under
 * {@code properties} and the assertion would pass against a body the SPA cannot read.
 */
class RequestRateLimitWebFilterTest {

    private static final int API_CAPACITY = 2;
    private static final int SIGNUP_CAPACITY = 1;

    private static final String CATALOGUE = "/api/products";
    private static final String SIGNUP = "/api/signup";

    private final AtomicInteger forwarded = new AtomicInteger();
    private final WebFilterChain chain = exchange -> {
        forwarded.incrementAndGet();
        return Mono.empty();
    };

    private final RequestRateLimitWebFilter filter = new RequestRateLimitWebFilter(
            new RequestRateLimitBuckets(GatewayProperties.with(GatewayProperties.rateLimit(
                    API_CAPACITY, SIGNUP_CAPACITY))),
            writer());

    @Test
    @DisplayName("answers 429 with the ProblemDetail of the contract once the budget is spent")
    void answers429OnceTheBudgetIsSpent() {
        MockServerWebExchange refused = spendAndGoOneOver(CATALOGUE, API_CAPACITY);

        String body = refused.getResponse().getBodyAsString().block();
        assertSoftly(softly -> {
            softly.assertThat(refused.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            softly.assertThat(refused.getResponse().getHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            softly.assertThat(refused.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                    .isNotNull();
            softly.assertThat(body)
                    .contains("\"type\":\"https://ukun.dev/errors/rate-limited\"")
                    .contains("\"title\":\"Rate limited\"")
                    .contains("\"status\":429")
                    .contains("\"code\":\"RATE_LIMITED\"")
                    .contains("\"retryAfterSeconds\":")
                    .containsPattern("\"traceId\":\"[0-9a-f]{32}\"");
            softly.assertThat(forwarded).hasValue(API_CAPACITY);
        });
    }

    /**
     * The {@code Retry-After} header and the {@code retryAfterSeconds} extension are the same number
     * published twice — one for a client that does not know this contract, one for the SPA, which
     * reads the body and never the headers.
     */
    @Test
    @DisplayName("publishes the same wait in the header and in the body")
    void publishesTheSameWaitInTheHeaderAndInTheBody() {
        MockServerWebExchange refused = spendAndGoOneOver(CATALOGUE, API_CAPACITY);

        String retryAfter = refused.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER);

        assertThat(refused.getResponse().getBodyAsString().block())
                .contains("\"retryAfterSeconds\":%s".formatted(retryAfter));
    }

    /**
     * The half of "login and signup" this service actually sees. The budgets are separate, so a
     * script hammering the signup endpoint cannot take the shop down for the person browsing it —
     * and, the other way round, a busy catalogue never softens the limit that protects signup.
     */
    @Test
    @DisplayName("charges signup to its own budget, not the general one")
    void chargesSignupToItsOwnBudget() {
        spendAndGoOneOver(SIGNUP, SIGNUP_CAPACITY);

        assertThat(filter.filter(exchangeOf(CATALOGUE), chain).block()).isNull();
        assertThat(forwarded).hasValue(SIGNUP_CAPACITY + 1);
    }

    /**
     * Everything the gateway serves outside {@code /api/**} is deliberately unmetered — the product
     * photos of a single page view would spend a browsing budget on their own, and the actuator is
     * what the container probes to decide the service is alive.
     */
    @Test
    @DisplayName("leaves everything outside /api/** unmetered")
    void leavesEverythingOutsideApiUnmetered() {
        spendAndGoOneOver(CATALOGUE, API_CAPACITY);
        forwarded.set(0);

        filter.filter(exchangeOf("/media/products/anything.webp"), chain).block();
        filter.filter(exchangeOf("/actuator/health"), chain).block();
        filter.filter(exchangeOf("/apix/not-the-api"), chain).block();

        assertThat(forwarded).hasValue(3);
    }

    /** Spends the whole budget of {@code path} and returns the exchange of the one refused after it. */
    private MockServerWebExchange spendAndGoOneOver(String path, int capacity) {
        for (int spent = 0; spent < capacity; spent++) {
            filter.filter(exchangeOf(path), chain).block();
        }
        MockServerWebExchange refused = exchangeOf(path);
        filter.filter(refused, chain).block();

        return refused;
    }

    /**
     * A {@code POST} for every path, including the catalogue, so that the method is never what tells
     * the tiers apart in this test — only {@code /api/signup} is.
     */
    private static MockServerWebExchange exchangeOf(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(path));
    }

    private static ProblemDetailWriter writer() {
        ObjectMapper mapper = JsonMapper.builder()
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
                .build();

        return new ProblemDetailWriter(mapper);
    }
}
