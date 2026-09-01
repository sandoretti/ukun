package dev.sandoretti.ukun.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.gateway.support.UpstreamStub;
import dev.sandoretti.ukun.gateway.web.RequestRateLimitBuckets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The rate limit of {@code docs/08-identidad-y-seguridad.md} §8 over a real socket, on the operation
 * the issue singles out and this service can actually see: {@code POST /api/signup}. Which request is
 * charged to which budget is settled without a server in {@code RequestRateLimitWebFilterTest}; what
 * needs a port is the shape of the refusal.
 *
 * <p><strong>There is no login test here and there cannot be one.</strong> The login form posts to
 * {@code /login} on {@code ukun-auth}'s own origin — this gateway routes {@code /login/oauth2/**} and
 * nothing else under that prefix — so its budget lives in {@code LoginRateLimiter} of that service,
 * where {@code LoginRateLimitIT} covers it.
 *
 * <p>The buckets are handed back around every method because they are per-JVM state and this context
 * is shared with every other {@code *IT} of the service: a budget left spent here would throttle
 * whichever class runs next, and one left spent by another class would make this one pass for the
 * wrong reason.
 */
class RateLimitIT extends GatewayIntegrationTest {

    /** {@code ukun.gateway.rate-limit.signup.capacity}, from {@code application.yaml}. */
    private static final int SIGNUP_BUDGET = 5;

    private static final String SIGNUP = "/api/signup";

    private static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    private static final String STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    private static final String CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String REFERRER_POLICY = "Referrer-Policy";

    @Autowired
    private RequestRateLimitBuckets buckets;

    @BeforeEach
    void startWithEveryBudgetFull() {
        buckets.discardAll();
    }

    @AfterEach
    void handBackEveryBudget() {
        buckets.discardAll();
    }

    @Test
    @DisplayName("answers 429 with the ProblemDetail of the contract once the signup budget is spent")
    void answers429OnceTheSignupBudgetIsSpent() {
        spendTheSignupBudget();

        signUp().expectStatus()
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader()
                .exists(HttpHeaders.RETRY_AFTER)
                .expectBody()
                .jsonPath("$.type")
                .isEqualTo("https://ukun.dev/errors/rate-limited")
                .jsonPath("$.title")
                .isEqualTo("Rate limited")
                .jsonPath("$.status")
                .isEqualTo(429)
                .jsonPath("$.code")
                .isEqualTo("RATE_LIMITED")
                .jsonPath("$.retryAfterSeconds")
                .isNumber()
                .jsonPath("$.traceId")
                .isNotEmpty();

        // The refused one was cut here, not forwarded and answered upstream.
        assertThat(UpstreamStub.API.requestedPaths()).hasSize(SIGNUP_BUDGET);
    }

    /**
     * The reason the filter sits at {@code @Order(2)} instead of in front of the security chain: the
     * four headers of {@code docs/08} §8 are written from inside that chain, in a {@code beforeCommit}
     * hook, so a 429 emitted earlier would go out with none of them. Fixed as behaviour here rather
     * than left to the comment that explains it.
     */
    @Test
    @DisplayName("dresses the 429 in the four security headers")
    void dressesThe429InTheFourSecurityHeaders() {
        spendTheSignupBudget();

        signUp().expectStatus()
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader()
                .exists(CONTENT_SECURITY_POLICY)
                .expectHeader()
                .exists(STRICT_TRANSPORT_SECURITY)
                .expectHeader()
                .valueEquals(CONTENT_TYPE_OPTIONS, "nosniff")
                .expectHeader()
                .valueEquals(REFERRER_POLICY, "strict-origin-when-cross-origin");
    }

    /**
     * A budget spent on signup must not close the shop. The two tiers are separate precisely so that
     * a script pointed at the one endpoint worth protecting cannot deny the catalogue to everybody
     * behind the same address.
     */
    @Test
    @DisplayName("leaves the catalogue open when the signup budget is spent")
    void leavesTheCatalogueOpenWhenTheSignupBudgetIsSpent() {
        spendTheSignupBudget();

        http.get().uri("/api/products").exchange().expectStatus().isOk();
    }

    private void spendTheSignupBudget() {
        for (int spent = 0; spent < SIGNUP_BUDGET; spent++) {
            signUp().expectStatus().isOk();
        }
    }

    /**
     * Every request carries a CSRF token: since issue #28 that check runs before routing, before the
     * size limit and before this filter, so a mutation without one would never reach the budget.
     */
    private WebTestClient.ResponseSpec signUp() {
        return csrfProtected
                .post()
                .uri(SIGNUP)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange();
    }
}
