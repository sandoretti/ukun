package dev.sandoretti.ukun.gateway.session;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import org.springframework.core.serializer.DefaultDeserializer;
import org.springframework.core.serializer.DefaultSerializer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.MapSession;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * The BFF's session, in {@code ukun_gateway} instead of in this process's heap (ADR-0024, issue
 * #118).
 *
 * <h2>Why this class exists at all</h2>
 *
 * Spring Session publishes exactly three artifacts — {@code core}, {@code data-redis} and
 * {@code jdbc} — and the last one is servlet only: there is no {@code ReactiveSessionRepository} over
 * JDBC to depend on. ADR-0024 chose Postgres over Redis for the deployment budget of ADR-0023, and
 * this class is the price of that choice, written down there as such. {@code ukun-auth}, being a
 * servlet application, does get to use the library.
 *
 * <h2>What it buys, and what it does not</h2>
 *
 * The gateway stays at one replica. What a persistent session buys here is <strong>surviving a
 * deployment</strong> — the same argument as the signing key of {@code ukun-auth} — and in the
 * ephemeral environment of ADR-0023 that happens on every demo. It does not buy concurrency today;
 * it makes {@code max>1} a template change rather than a design change.
 *
 * <h2>What is in the row</h2>
 *
 * The security context of whoever signed in, and the {@code OAuth2AuthorizedClient} with its access
 * and refresh token. That makes {@code web_session.attributes} as sensitive as
 * {@code account.password_hash} is in {@code ukun_auth}: it is never logged, never returned, and read
 * only by this class ({@code docs/11-observabilidad-y-logging.md} §4).
 *
 * <p><strong>The tokens are only here because a bean puts them here.</strong> Boot's default
 * {@code ServerOAuth2AuthorizedClientRepository} keeps them in a map of this JVM, so
 * {@code ClientRegistrationConfig.authorizedClientRepository()} declares the session-backed one
 * instead. Without it this class persists the principal and nothing else, and the visible symptom is
 * a {@code 302} towards {@code /oauth2/authorize} after a restart rather than a session error.
 *
 * <h2>Blocking, on purpose, off the event loop</h2>
 *
 * JDBC blocks and this service is the single entry point of the whole system, so a blocking call on
 * an event loop thread would not degrade one request, it would degrade all of them. Every statement
 * here goes through {@link #blocking(Callable)} onto a bounded scheduler. That is the shape ADR-0024
 * accepted when it chose one driver and one pool over adding R2DBC beside Liquibase's JDBC one.
 */
public final class JdbcWebSessionRepository implements ReactiveSessionRepository<MapSession> {

    /**
     * One statement, not read-then-write. The session is saved on nearly every request, and a
     * {@code SELECT} followed by an {@code INSERT} or an {@code UPDATE} would be two round trips and a
     * race between them for exactly no benefit: the row is written whole either way.
     */
    private static final String UPSERT =
            """
            INSERT INTO web_session (id, created_at, last_accessed_at, max_inactive_interval, expires_at, attributes)
                 VALUES (:id, :createdAt, :lastAccessedAt, :maxInactiveInterval, :expiresAt, :attributes)
            ON CONFLICT (id) DO UPDATE
                    SET last_accessed_at = excluded.last_accessed_at,
                        max_inactive_interval = excluded.max_inactive_interval,
                        expires_at = excluded.expires_at,
                        attributes = excluded.attributes
            """;

    /**
     * Expiry is decided by the {@code WHERE}, not by {@link MapSession#isExpired()}. That method reads
     * the system clock directly, and this service injects a {@link Clock} so a test can move time
     * without rewriting a column behind the repository's back (skill {@code testing}).
     */
    private static final String FIND_BY_ID =
            """
            SELECT id, created_at, last_accessed_at, max_inactive_interval, attributes
              FROM web_session
             WHERE id = :id AND expires_at > :now
            """;

    private static final String DELETE_BY_ID = "DELETE FROM web_session WHERE id = :id";

    private static final String DELETE_EXPIRED = "DELETE FROM web_session WHERE expires_at <= :now";

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final Scheduler scheduler;
    private final Clock clock;
    private final Duration defaultMaxInactiveInterval;

    JdbcWebSessionRepository(
            JdbcClient jdbc,
            TransactionTemplate transactions,
            Scheduler scheduler,
            Clock clock,
            Duration defaultMaxInactiveInterval) {

        this.jdbc = jdbc;
        this.transactions = transactions;
        this.scheduler = scheduler;
        this.clock = clock;
        this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
    }

    /**
     * Nothing is written here. {@code SpringSessionWebSessionStore} creates a session for every
     * request that asks for one and saves only the ones that end up with something in them — writing
     * on creation would put a row in the table for every anonymous visit to the catalogue.
     */
    @Override
    public Mono<MapSession> createSession() {
        return Mono.fromSupplier(() -> {
            MapSession session = new MapSession();
            session.setMaxInactiveInterval(defaultMaxInactiveInterval);
            return session;
        });
    }

    @Override
    public Mono<Void> save(MapSession session) {
        return blocking(() -> {
                    transactions.executeWithoutResult(status -> {
                        // Spring Security rotates the session id when somebody signs in, which is what
                        // stops session fixation. The row under the old id has to go with it, and in
                        // the same transaction: two statements that can half-apply would leave either
                        // an orphan row or none at all.
                        if (!session.getId().equals(session.getOriginalId())) {
                            deleteRow(session.getOriginalId());
                        }
                        upsert(session);
                    });
                    return null;
                })
                .then();
    }

    @Override
    public Mono<MapSession> findById(String id) {
        return blocking(() -> jdbc.sql(FIND_BY_ID)
                .param("id", id)
                .param("now", now())
                .query(this::toSession)
                .optional()
                .orElse(null));
    }

    @Override
    public Mono<Void> deleteById(String id) {
        return blocking(() -> {
                    deleteRow(id);
                    return null;
                })
                .then();
    }

    /**
     * Without this the table only grows: a session that expires is never asked for again, so nothing
     * would ever delete its row. It is a {@code DELETE … WHERE}, so it carries no ShedLock — running
     * it on two replicas would be waste and not a defect (ADR-0024), and today there is only one.
     */
    @Scheduled(fixedDelayString = "${ukun.session.sweep-interval:PT10M}")
    public void deleteExpiredSessions() {
        transactions.executeWithoutResult(
                status -> jdbc.sql(DELETE_EXPIRED).param("now", now()).update());
    }

    private void upsert(MapSession session) {
        Instant lastAccessed = session.getLastAccessedTime();
        Duration maxInactive = session.getMaxInactiveInterval();

        jdbc.sql(UPSERT)
                .param("id", session.getId())
                .param("createdAt", at(session.getCreationTime()))
                .param("lastAccessedAt", at(lastAccessed))
                .param("maxInactiveInterval", (int) maxInactive.toSeconds())
                .param("expiresAt", at(lastAccessed.plus(maxInactive)))
                .param("attributes", serialize(attributesOf(session)))
                .update();
    }

    private void deleteRow(String id) {
        jdbc.sql(DELETE_BY_ID).param("id", id).update();
    }

    private MapSession toSession(ResultSet rs, int rowNum) throws SQLException {
        MapSession session = new MapSession(rs.getString("id"));
        session.setCreationTime(rs.getObject("created_at", OffsetDateTime.class).toInstant());
        session.setLastAccessedTime(
                rs.getObject("last_accessed_at", OffsetDateTime.class).toInstant());
        session.setMaxInactiveInterval(Duration.ofSeconds(rs.getInt("max_inactive_interval")));

        deserialize(rs.getBytes("attributes")).forEach(session::setAttribute);

        return session;
    }

    private static Map<String, Object> attributesOf(MapSession session) {
        Map<String, Object> attributes = new HashMap<>();
        session.getAttributeNames().forEach(name -> attributes.put(name, session.getAttribute(name)));
        return attributes;
    }

    /**
     * JDK serialization, the same mechanism {@code spring-session-jdbc} uses for the same job. The
     * bytes never leave this database and never come from a caller: what is deserialised here is what
     * this class wrote, which is the condition that makes it safe.
     */
    private static byte[] serialize(Map<String, Object> attributes) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            new DefaultSerializer().serialize(attributes, bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Could not serialise the session attributes", e);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deserialize(byte[] attributes) {
        try {
            Object read = new DefaultDeserializer(JdbcWebSessionRepository.class.getClassLoader())
                    .deserialize(new ByteArrayInputStream(attributes));
            return (Map<String, Object>) read;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the session attributes back", e);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private <T> Mono<T> blocking(Callable<T> work) {
        return Mono.fromCallable(work).subscribeOn(scheduler);
    }
}
