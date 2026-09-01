package dev.sandoretti.ukun.gateway.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The single Postgres container of this service's suite, for the session table ADR-0024 gave it.
 *
 * <p>This service had no database until v1.0 and the two fixtures said so in writing. It has one now,
 * and every context that starts needs it: the {@code WebSessionManager} is backed by
 * {@link dev.sandoretti.ukun.gateway.session.JdbcWebSessionRepository}, so without a datasource the
 * context does not come up at all — which is the honest shape of the change, and the reason this is
 * not made optional behind a profile.
 *
 * <p>One database and no {@code useDatabase(...)} switch, unlike {@code ukun-auth}: there is no
 * {@code demo} context here and nothing seeds this table, so no two suites need incompatible states
 * of it. It is started from a static initialiser rather than with {@code @Testcontainers}, for the
 * reason the other services record — that annotation stops the container after the last test of
 * <em>each</em> class, which is how a shared container quietly becomes one per class.
 */
public final class GatewayDatabase {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    /**
     * Called from each fixture's own {@code @DynamicPropertySource}, never from a shared one: that
     * method takes part in the context cache key, and an inherited one would give two suites the same
     * key (skill {@code testing}).
     */
    public static void bind(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private GatewayDatabase() {}
}
