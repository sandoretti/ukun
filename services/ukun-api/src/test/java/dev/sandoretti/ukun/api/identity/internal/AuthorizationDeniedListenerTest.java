package dev.sandoretti.ukun.api.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * What {@code auth.authorization.denied} says about <em>who</em> was turned away.
 *
 * <p>The field under test is {@code accountId}, and the reason it needs a test of its own is that the
 * two candidate values normally coincide: with the configuration this service ships,
 * {@code JwtAuthenticationToken.getName()} returns the {@code sub}, so reading the name and reading
 * the subject are indistinguishable. They stop coinciding the moment somebody sets
 * {@code spring.security.oauth2.resourceserver.jwt.principal-claim-name} — a property that sits in the
 * same block of {@code application.yaml} as {@code authorities-claim-name}, and that would then put
 * the caller's email in a business event that {@code docs/11-observabilidad-y-logging.md} §3 does not
 * list among the three allowed to carry one.
 *
 * <p>So the token here is built the way that property builds it: with the three-argument constructor,
 * whose {@code name} is the claim it selects. A listener that reads the principal's name fails these
 * assertions; one that reads the token's subject cannot be changed by configuration at all.
 */
class AuthorizationDeniedListenerTest {

    private static final String BUSINESS_LOGGER = "ukun.business";

    private static final String EVENT = "auth.authorization.denied";

    private static final String ACCOUNT_ID = UUID.randomUUID().toString();

    /** What {@code principal-claim-name: email} would make {@code getName()} return. */
    private static final String EMAIL = "ada@example.com";

    private final AuthorizationDeniedListener listener = new AuthorizationDeniedListener();

    private final ListAppender<ILoggingEvent> lines = new ListAppender<>();

    private Logger businessLog;

    @BeforeEach
    void captureTheBusinessLog() {
        businessLog = (Logger) LoggerFactory.getLogger(BUSINESS_LOGGER);
        lines.start();
        businessLog.addAppender(lines);
    }

    @AfterEach
    void releaseTheBusinessLog() {
        businessLog.detachAppender(lines);
        lines.stop();
        MDC.clear();
    }

    @Test
    @DisplayName("publishes the subject as accountId even when the principal is named by email")
    void shouldPublishTheSubjectEvenWhenThePrincipalIsNamedByEmail() {
        Authentication authentication = jwtNamed(EMAIL);

        listener.onAuthorizationDenied(denialOf(authentication));

        assertThat(lines.list).singleElement().satisfies(line -> {
            assertThat(fieldsOf(line))
                    .containsEntry("accountId", ACCOUNT_ID)
                    .containsEntry("role", "CUSTOMER")
                    .containsEntry("reason", "ROLE");
            assertThat(line.getFormattedMessage()).doesNotContain(EMAIL);
        });
    }

    /**
     * Every request to a protected route without a token is denied here first, so an indiscriminate
     * listener would turn one port scan into a flood of WARN lines about nobody. The anonymous case is
     * already a {@code 401} in the access log.
     */
    @Test
    @DisplayName("an anonymous caller produces no event at all")
    void shouldPublishNothingForAnAnonymousCaller() {
        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        listener.onAuthorizationDenied(denialOf(anonymous));

        assertThat(lines.list).isEmpty();
    }

    /**
     * The fields travel as the second argument of the log call — a map, not text — which is what a
     * structured appender indexes and therefore what a test should read.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> fieldsOf(ILoggingEvent line) {
        assertThat(line.getArgumentArray()).hasSize(2);
        assertThat(line.getArgumentArray()[0]).isEqualTo(EVENT);

        return (Map<String, String>) line.getArgumentArray()[1];
    }

    /** A token whose principal name is deliberately not its subject, as the property would make it. */
    private static Authentication jwtNamed(String name) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(ACCOUNT_ID)
                .claim("email", EMAIL)
                .claim("roles", List.of("CUSTOMER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();

        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")), name);
    }

    private static AuthorizationDeniedEvent<String> denialOf(Authentication authentication) {
        Supplier<Authentication> caller = () -> authentication;

        return new AuthorizationDeniedEvent<>(caller, "a protected method", new AuthorizationDecision(false));
    }
}
