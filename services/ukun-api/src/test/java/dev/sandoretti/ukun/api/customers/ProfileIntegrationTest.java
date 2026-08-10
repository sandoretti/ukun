package dev.sandoretti.ukun.api.customers;

import dev.sandoretti.ukun.api.AbstractIntegrationTest;
import dev.sandoretti.ukun.api.JwksTestServer;
import dev.sandoretti.ukun.api.customers.domain.Customer;
import dev.sandoretti.ukun.api.customers.domain.ProfileLocale;
import dev.sandoretti.ukun.api.stores.StoreMother;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The fixture the two {@code /api/me} suites share: a running application, a real Postgres, and a real
 * JWKS served over HTTP so the tokens these tests carry are validated the way production validates
 * them.
 *
 * <p>Tokens rather than {@code SecurityMockMvcRequestPostProcessors.jwt()}, and the difference is the
 * whole subject of this slice: the endpoints answer <em>whoever the token says</em>, so a test that
 * injects an authentication into the context skips the one step where the identity is established. The
 * {@code sub} claim reaching {@code customer.account_id} is what is under test.
 *
 * <p>The Spring annotations and the {@code @DynamicPropertySource} live here rather than on each
 * subclass so that both share one application context — the context cache key includes those methods,
 * so declaring them twice would mean two contexts and two Liquibase runs for one configuration.
 *
 * <p>A single store is seeded because {@code favoriteStoreId} points at one, and the network of
 * {@code StoreMother} is reused instead of inventing another: these suites care that the identifier
 * resolves, not what is in it.
 */
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.liquibase.contexts=prod", "spring.jpa.properties.hibernate.generate_statistics=true"})
abstract class ProfileIntegrationTest extends AbstractIntegrationTest {

    protected static final String ME = "/api/me";
    protected static final String MY_PROFILE = "/api/me/profile";

    /**
     * Started once and stopped by a shutdown hook rather than by an {@code @AfterAll}, for the reason
     * the Postgres container of {@link AbstractIntegrationTest} is: it is shared by every subclass, and
     * the first suite to finish would otherwise close it under the feet of the next one.
     */
    private static final JwksTestServer AUTH = JwksTestServer.start();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(ProfileIntegrationTest::stopAuth));
    }

    @DynamicPropertySource
    static void profileEnvironment(DynamicPropertyRegistry registry) {
        useDatabase(registry, PROFILE_DATABASE);

        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", AUTH::jwkSetUri);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> JwksTestServer.ISSUER);
    }

    @Autowired
    protected RestTestClient restTestClient;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seedTheStoreNetwork() {
        inATransaction(() -> List.of(StoreMother.alcalaStore(), StoreMother.barcelonaStore())
                .forEach(store -> {
                    entityManager.persist(store.getAddress());
                    entityManager.persist(store);
                }));
    }

    /**
     * One statement for the four tables because {@code address} holds both a store's address and a
     * customer's: truncating them separately would leave whichever went first pinned by the other's
     * {@code ON DELETE RESTRICT}.
     */
    @AfterEach
    void clearTheProfiles() {
        jdbc.execute("TRUNCATE TABLE customer, card, store, address RESTART IDENTITY CASCADE");
    }

    /**
     * Committed before any request is made: the endpoint is served on another thread, so rows left
     * inside an open test transaction would be invisible to it. One {@code persist} for the three rows,
     * through the cascade that {@link Customer} owns them with.
     */
    protected void seed(Customer customer) {
        inATransaction(() -> entityManager.persist(customer));
    }

    /**
     * The only way to give a customer a language other than the default: {@code locale} is
     * {@code insertable = false}, so the column's {@code DEFAULT} decides it at insert time and
     * everything afterwards goes through {@code PUT /api/me/profile}.
     */
    protected void setLocale(UUID accountId, ProfileLocale locale) {
        jdbc.update("UPDATE customer SET locale = ? WHERE account_id = ?", locale.code(), accountId);
    }

    protected String customerToken(UUID accountId, String email) {
        return AUTH.validToken(accountId, email, List.of("CUSTOMER"));
    }

    protected String employeeToken(UUID accountId, String email) {
        return AUTH.validToken(accountId, email, List.of("EMPLOYEE"));
    }

    protected long storeIdOf(String name) {
        return jdbc.queryForObject("SELECT id FROM store WHERE name = ?", Long.class, name);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    private static void stopAuth() {
        try {
            AUTH.close();
        } catch (Exception e) {
            // Nothing to recover on the way out of the JVM; the port goes with the process.
        }
    }
}
