package dev.sandoretti.ukun.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sandoretti.ukun.gateway.session.JdbcWebSessionRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.session.MapSession;

/**
 * That the BFF's session really is a row of {@code web_session} and not the heap (ADR-0024).
 *
 * <p>It extends {@link GatewayIntegrationTest} rather than declaring a {@code @SpringBootTest} of its
 * own so that it reuses that context instead of starting a fourth one — the cost the {@code testing}
 * skill warns about, whose symptom appears in somebody else's class as {@code too many clients}. What
 * it needs from that fixture is only the beans; it makes no HTTP call.
 *
 * <p>The four facts here are the ones a replica or a deployment would break. Every one of them is
 * asserted against the table, because "the session survives" is a claim about the database and
 * asserting it against the repository's own in-memory answer would pass with no persistence at all.
 */
class WebSessionPersistenceIT extends GatewayIntegrationTest {

    private static final String ATTRIBUTE = "ukun.test.attribute";

    @Autowired
    private JdbcWebSessionRepository sessions;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ServerOAuth2AuthorizedClientRepository authorizedClients;

    /**
     * The bean without which the whole of ADR-0024 is half true for this service.
     *
     * <p>Boot autoconfigures {@code ServerOAuth2AuthorizedClientRepository} over an
     * <em>in-memory</em> {@code ReactiveOAuth2AuthorizedClientService}
     * ({@code ReactiveOAuth2ClientWebSecurityAutoConfiguration}), so the access and refresh tokens
     * would live in a map of this JVM even with the session in Postgres. Measured with two replicas
     * up: after restarting the gateway the session came back — the access log even carried the
     * {@code accountId} — and the first call to {@code /api/**} still answered {@code 302} towards
     * {@code /oauth2/authorize}, because there was no token left to relay. It reads as "the session
     * did not survive" and it is not that.
     *
     * <p>Asserting the type and not the behaviour is the point: losing this bean is a deletion, and a
     * deletion is silent — Boot puts its own back.
     */
    @Test
    @DisplayName("the authorized client repository is the session-backed one, not Boot's in-memory default")
    void keepsTheRelayedTokensInTheSession() {
        assertThat(authorizedClients).isInstanceOf(WebSessionServerOAuth2AuthorizedClientRepository.class);
    }

    @Test
    @DisplayName("a saved session is a row, and it comes back with its attributes")
    void savesAndReadsBackASession() {
        MapSession session = sessions.createSession().block();
        session.setAttribute(ATTRIBUTE, "value");

        sessions.save(session).block();

        assertThat(rowsWithId(session.getId())).isOne();
        assertThat(sessions.findById(session.getId()).block())
                .satisfies(read -> assertThat(read.<String>getAttribute(ATTRIBUTE)).isEqualTo("value"));
    }

    /**
     * Spring Security rotates the id when somebody signs in, which is what stops session fixation.
     * Leaving the old row behind would keep a usable session under the id an attacker planted — the
     * defect the rotation exists to prevent, moved from memory into a table.
     */
    @Test
    @DisplayName("rotating the session id leaves no row under the old one")
    void dropsTheRowOfTheOldIdWhenTheIdIsRotated() {
        MapSession session = sessions.createSession().block();
        sessions.save(session).block();
        String oldId = session.getId();

        session.changeSessionId();
        sessions.save(session).block();

        assertThat(rowsWithId(oldId)).isZero();
        assertThat(rowsWithId(session.getId())).isOne();
    }

    @Test
    @DisplayName("an expired session is not handed back")
    void doesNotReturnAnExpiredSession() {
        MapSession session = expiredSession();

        sessions.save(session).block();

        assertThat(sessions.findById(session.getId()).block()).isNull();
    }

    /**
     * Nothing ever asks for an expired session again, so without the sweep its row would stay for
     * good and the table would only grow — the same shape of defect as the two checkout tables of
     * {@code ukun-api}.
     */
    @Test
    @DisplayName("the sweep deletes what expired and leaves what did not")
    void sweepsOnlyTheExpiredSessions() {
        MapSession expired = expiredSession();
        MapSession live = sessions.createSession().block();
        sessions.save(expired).block();
        sessions.save(live).block();

        sessions.deleteExpiredSessions();

        assertThat(rowsWithId(expired.getId())).isZero();
        assertThat(rowsWithId(live.getId())).isOne();
    }

    /**
     * Aged by moving the session's last access into the past rather than by moving a clock: the row's
     * {@code expires_at} is derived from that instant, so this produces a genuinely expired row
     * without a test writing a column the repository owns.
     */
    private MapSession expiredSession() {
        MapSession session = sessions.createSession().block();
        session.setMaxInactiveInterval(Duration.ofMinutes(1));
        session.setLastAccessedTime(Instant.now().minus(Duration.ofMinutes(10)));
        return session;
    }

    private int rowsWithId(String id) {
        return jdbc.sql("SELECT count(*) FROM web_session WHERE id = :id")
                .param("id", id)
                .query(Integer.class)
                .single();
    }
}
