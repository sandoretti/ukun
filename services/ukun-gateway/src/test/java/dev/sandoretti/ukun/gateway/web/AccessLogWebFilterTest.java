package dev.sandoretti.ukun.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.sandoretti.ukun.gateway.security.UkunOidcUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * The one line the gateway writes per request. It is tested by reading the log rather than by
 * inspecting the filter's fields, because everything worth asserting here <em>is</em> the line: what
 * it contains, what it must never contain, and at which level it comes out.
 *
 * <p>This is the only test in the repo that captures log output, and the reason is
 * {@code docs/11-observabilidad-y-logging.md} §4. That section forbids logging {@code Authorization}
 * and {@code Cookie}, and a prohibition nobody checks is a comment. An appender is the only place the
 * check can actually be made.
 */
class AccessLogWebFilterTest {

    private static final String ACCESS_LOGGER = "ukun.access";

    private static final String CATALOGUE = "/api/products";

    private static final String ACCOUNT_ID = UUID.randomUUID().toString();

    /** What {@code getName()} would return for this principal, and what must never reach a log line. */
    private static final String EMAIL = "ada@example.com";

    private final AccessLogWebFilter filter = new AccessLogWebFilter();

    private final ListAppender<ILoggingEvent> lines = new ListAppender<>();

    private Logger accessLog;

    @BeforeEach
    void captureTheAccessLog() {
        accessLog = (Logger) LoggerFactory.getLogger(ACCESS_LOGGER);
        lines.start();
        accessLog.addAppender(lines);
    }

    @AfterEach
    void releaseTheAccessLog() {
        accessLog.detachAppender(lines);
        lines.stop();
        MDC.clear();
    }

    @Test
    @DisplayName("writes one line with method, path, status and duration")
    void writesOneLinePerRequest() {
        MockServerWebExchange exchange = exchangeFor(CATALOGUE);

        filter.filter(exchange, respondingWith(HttpStatus.OK)).block();

        assertThat(lines.list)
                .singleElement()
                .extracting(ILoggingEvent::getFormattedMessage)
                .asString()
                .startsWith("GET %s 200 ".formatted(CATALOGUE))
                .endsWith("ms");
    }

    /**
     * {@code docs/11} §4: no body, no {@code Authorization}, no {@code Cookie}. The request carries
     * both headers and a session cookie precisely so that a future "let's log the headers, it helps
     * when debugging" fails here instead of in production.
     */
    @Test
    @DisplayName("never writes the Authorization header, the cookies or the query string")
    void neverWritesACredential() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(
                        CATALOGUE + "?name=sofa")
                .header(HttpHeaders.AUTHORIZATION, "Bearer a-real-looking-token")
                .header(HttpHeaders.COOKIE, "SESSION=6f0b1e2c; XSRF-TOKEN=1a2b3c"));

        filter.filter(exchange, respondingWith(HttpStatus.OK)).block();

        assertThat(lines.list)
                .singleElement()
                .extracting(ILoggingEvent::getFormattedMessage)
                .asString()
                .doesNotContain("Bearer", "SESSION", "XSRF-TOKEN", "name=sofa");
    }

    /**
     * The mechanical rule of {@code docs/11} §7, which exists because the legacy logged a failed form
     * validation at ERROR: if everything is an error, ERROR stops meaning anything and nobody reads
     * the log at all.
     */
    @Test
    @DisplayName("a 4xx is a warning, a 5xx is an error and everything else is information")
    void gradesTheLevelByTheStatus() {
        assertSoftly(softly -> {
            softly.assertThat(levelFor(HttpStatus.OK)).isEqualTo(Level.INFO);
            softly.assertThat(levelFor(HttpStatus.FOUND)).isEqualTo(Level.INFO);
            softly.assertThat(levelFor(HttpStatus.UNAUTHORIZED)).isEqualTo(Level.WARN);
            softly.assertThat(levelFor(HttpStatus.NOT_FOUND)).isEqualTo(Level.WARN);
            softly.assertThat(levelFor(HttpStatus.INTERNAL_SERVER_ERROR)).isEqualTo(Level.ERROR);
        });
    }

    /**
     * The half of {@code docs/11} §5 this service is responsible for. {@code accountId} is the UUID
     * from the access token, never {@code Principal#getName()} — which in this gateway is the email
     * the person typed into the login form, and is PII that would then appear on every line of every
     * request.
     */
    @Test
    @DisplayName("names the person by account id and never by email")
    void namesThePersonByAccountId() {
        MockServerWebExchange exchange = exchangeFor(CATALOGUE);
        ServerWebExchange loggedIn =
                exchange.mutate().principal(Mono.just(loggedInUser())).build();

        filter.filter(loggedIn, respondingWith(HttpStatus.OK)).block();

        assertSoftly(softly -> {
            softly.assertThat(lines.list)
                    .singleElement()
                    .extracting(event -> event.getMDCPropertyMap().get("accountId"))
                    .isEqualTo(ACCOUNT_ID);
            softly.assertThat(lines.list.getFirst().getFormattedMessage()).doesNotContain(EMAIL);
            softly.assertThat(lines.list.getFirst().getMDCPropertyMap()).doesNotContainValue(EMAIL);
        });
    }

    /**
     * The event loop is shared by every request in flight. A key left in the MDC here would not merely
     * be stale — it would attach itself to whatever the thread picks up next, which is somebody else.
     */
    @Test
    @DisplayName("leaves no account behind on the event loop thread")
    void leavesNothingBehindOnTheEventLoop() {
        MockServerWebExchange exchange = exchangeFor(CATALOGUE);
        ServerWebExchange loggedIn =
                exchange.mutate().principal(Mono.just(loggedInUser())).build();

        filter.filter(loggedIn, respondingWith(HttpStatus.OK)).block();

        assertThat(MDC.get("accountId")).isNull();
    }

    /**
     * An anonymous request to the public catalogue still gets its line — there is simply nobody to
     * name. The field is absent rather than filled with a placeholder.
     */
    @Test
    @DisplayName("an anonymous request is logged without an account")
    void logsAnAnonymousRequestWithoutAnAccount() {
        MockServerWebExchange exchange = exchangeFor(CATALOGUE);

        filter.filter(exchange, respondingWith(HttpStatus.OK)).block();

        assertThat(lines.list).singleElement().satisfies(event -> assertThat(event.getMDCPropertyMap())
                .doesNotContainKey("accountId"));
    }

    /**
     * A principal of another shape — nothing in this service produces one today, but a future
     * authentication mechanism could — is treated as anonymous rather than named by whatever
     * {@code getName()} happens to return. Guessing is how the email would get in.
     */
    @Test
    @DisplayName("a principal that is not this gateway's user is not named either")
    void doesNotGuessTheAccountOfAnUnknownPrincipal() {
        MockServerWebExchange exchange = exchangeFor(CATALOGUE);
        Authentication other = new TestingAuthenticationToken(EMAIL, "n/a", "ROLE_CUSTOMER");
        ServerWebExchange loggedIn = exchange.mutate().principal(Mono.just(other)).build();

        filter.filter(loggedIn, respondingWith(HttpStatus.OK)).block();

        assertThat(lines.list).singleElement().satisfies(event -> assertSoftly(softly -> {
            softly.assertThat(event.getMDCPropertyMap()).doesNotContainKey("accountId");
            softly.assertThat(event.getFormattedMessage()).doesNotContain(EMAIL);
        }));
    }

    private Level levelFor(HttpStatus status) {
        lines.list.clear();
        filter.filter(exchangeFor(CATALOGUE), respondingWith(status)).block();

        return lines.list.getFirst().getLevel();
    }

    private static MockServerWebExchange exchangeFor(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path));
    }

    /** A chain that does what the routing filter does as far as this filter can see: it sets a status. */
    private static WebFilterChain respondingWith(HttpStatus status) {
        return exchange -> {
            exchange.getResponse().setStatusCode(status);
            return Mono.empty();
        };
    }

    private static Authentication loggedInUser() {
        OidcIdToken idToken = OidcIdToken.withTokenValue("an-id-token")
                // The id token names the person by email in this service; the account id lives in the
                // access token and is carried separately. That mismatch is the point of the test.
                .subject(EMAIL)
                .build();

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
        UkunOidcUser user = new UkunOidcUser(authorities, idToken, "sub", ACCOUNT_ID);

        return new OAuth2AuthenticationToken(user, authorities, "ukun");
    }
}
