package dev.sandoretti.ukun.api.staff;

import dev.sandoretti.ukun.api.AbstractIntegrationTest;
import dev.sandoretti.ukun.api.stores.StoreMother;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The two stores and the six people the employee suites share (RF-11).
 *
 * <p><strong>Two stores, both staffed, is the load-bearing part of the fixture.</strong> With a single
 * store every listing assertion here would pass even if the query ignored {@code store_id} altogether,
 * and the rule the whole slice exists to enforce — an admin manages their own store's staff and nobody
 * else's — would be untested. Elena administers Barcelona for exactly that reason.
 *
 * <p>Real tokens rather than an injected authentication, like the stock and profile suites: resolving
 * {@code sub} against {@code employee.account_id} <em>is</em> the step under test here, since the store
 * never travels in the request, and mocking the authentication would skip precisely that.
 */
@AutoConfigureRestTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.liquibase.contexts=prod")
abstract class StaffIntegrationTest extends AbstractIntegrationTest {

    protected static final String EMPLOYEES = "/api/admin/employees";

    protected static final String ALCALA = "Ukun Alcalá de Henares";
    protected static final String BARCELONA = "Ukun Barcelona";

    /** Administers Alcalá. Every request that is meant to succeed is hers. */
    protected static final UUID CARMEN = UUID.fromString("7d1b0e30-0000-4000-8000-0000000000c1");

    /** Works in Alcalá without administering it: in the listing, and denied by it. */
    protected static final UUID DIEGO = UUID.fromString("7d1b0e30-0000-4000-8000-0000000000d1");

    /** Also Alcalá, and the one the accent-folding search looks for. */
    protected static final UUID ANGELA = UUID.fromString("7d1b0e30-0000-4000-8000-0000000000a1");

    /** The two Luises of Alcalá, indistinguishable except by their id. */
    protected static final UUID LUIS_ONE = UUID.fromString("7d1b0e30-0000-4000-8000-0000000000e1");

    protected static final UUID LUIS_TWO = UUID.fromString("7d1b0e30-0000-4000-8000-0000000000e2");

    /** Administers Barcelona. She is how "somebody else's store" is spelled in these tests. */
    protected static final UUID ELENA = UUID.fromString("7d1b0e30-0000-4000-8000-0000000000b1");

    /** A perfectly valid admin token with no employee row behind it at all. */
    protected static final UUID GHOST = UUID.fromString("7d1b0e30-0000-4000-8000-0000000000f1");

    @DynamicPropertySource
    static void staffEnvironment(DynamicPropertyRegistry registry) {
        useDatabase(registry, STAFF_DATABASE);
        useJwks(registry);
    }

    @Autowired
    protected RestTestClient restTestClient;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seedTheStaff() {
        inATransaction(() -> List.of(StoreMother.alcalaStore(), StoreMother.barcelonaStore())
                .forEach(store -> {
                    entityManager.persist(store.getAddress());
                    entityManager.persist(store);
                }));

        long alcala = alcalaId();
        long barcelona = storeIdOf(BARCELONA);

        inATransaction(() -> {
            entityManager.persist(EmployeeMother.carmen(CARMEN, alcala));
            entityManager.persist(EmployeeMother.diego(DIEGO, alcala));
            entityManager.persist(EmployeeMother.angela(ANGELA, alcala));
            entityManager.persist(EmployeeMother.luis(LUIS_ONE, alcala));
            entityManager.persist(EmployeeMother.luis(LUIS_TWO, alcala));
            entityManager.persist(EmployeeMother.elena(ELENA, barcelona));
        });
    }

    /**
     * One statement, and {@code address} in it: a store's address is pinned by
     * {@code ON DELETE RESTRICT}, so truncating the two separately leaves whichever went first stuck.
     */
    @AfterEach
    void clearTheStaff() {
        jdbc.execute("TRUNCATE TABLE employee, store, address RESTART IDENTITY CASCADE");
    }

    protected long alcalaId() {
        return storeIdOf(ALCALA);
    }

    /** How many people work in a store, read straight off the table the endpoint writes. */
    protected int headcountOf(long storeId) {
        return jdbc.queryForObject("SELECT count(*) FROM employee WHERE store_id = ?", Integer.class, storeId);
    }

    protected RestTestClient.ResponseSpec get(String uri, String token) {
        return restTestClient
                .get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange();
    }

    /**
     * A page of employees as the client reads it — declared here rather than reusing
     * {@code PageResponse<EmployeeResponse>} so that a field the production types stop publishing shows
     * up as a missing value in a test, not as a type that changed underneath it.
     */
    protected record EmployeePage(List<Employee> items, int page, int size, long totalElements, int totalPages) {

        protected record Employee(
                Long id, String name, String lastName, String email, String jobTitle, Boolean isAdmin, Long storeId) {}
    }
}
