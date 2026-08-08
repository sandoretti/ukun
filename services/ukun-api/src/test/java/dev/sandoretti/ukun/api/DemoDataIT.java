package dev.sandoretti.ukun.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The demo dataset applied to an empty database, which is the criterion issue #10 states: under the
 * {@code demo} context the catalogue, the stores, the stock and the accounts are all there, and
 * nothing that belongs to a later release is.
 *
 * <p>Two of its assertions are worth more than a row count. The first is that both branches RF-01
 * distinguishes exist in the data — a product out of stock (a row with {@code quantity = 0}) and a
 * product not sold in that store at all (no row) — because the availability endpoint of v0.3 has to
 * tell them apart and cannot be developed against a case that never occurs. The second is that no
 * account UUID is shared between a customer and an employee: ADR-0006 makes the two roles mutually
 * exclusive, and the database enforces uniqueness inside each table but never across them.
 *
 * <p>Nothing is truncated here. Unlike {@link SchemaBaselineIT} this class writes no rows — the
 * seed data is exactly what it reads. That is also why it can share the seeded database of
 * {@link AbstractIntegrationTest} with {@link StackValidationIT}: both only read, and both need
 * the same twenty-seven changesets applied, so whichever context boots first migrates and the
 * other finds nothing left to do.
 */
@SpringBootTest(properties = "spring.liquibase.contexts=demo")
class DemoDataIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        useDatabase(registry, SEEDED_DATABASE);
    }

    /** Ten types of ten products each: enough for the pagination and type filter of v0.3. */
    private static final List<String> EXPECTED_TYPES = List.of(
            "Mesas",
            "Sillas",
            "Camas",
            "Escritorios",
            "Sofás",
            "Decoración",
            "Estanterías",
            "Iluminación",
            "Mesillas de noche",
            "Sillones y butacas");

    /** Coverage decreasing from the online store outwards, per the comment in the demo changelog. */
    private static final Map<String, Long> EXPECTED_STOCK_LINES_PER_STORE = Map.of(
            "Ukun Online", 90L,
            "Ukun Alcalá de Henares", 70L,
            "Ukun Torrejón de Ardoz", 60L,
            "Ukun Barcelona", 50L);

    /** The nineteen of the baseline plus the eight of the demo changelog. */
    private static final int EXPECTED_CHANGESETS = 27;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("the catalogue has ten types with ten products each")
    void seedsTheWholeCatalogue() {
        assertThat(jdbc.queryForList("SELECT name FROM product_type", String.class))
                .containsExactlyInAnyOrderElementsOf(EXPECTED_TYPES);

        assertThat(countOf("product")).isEqualTo(100);

        List<Long> perType = jdbc.queryForList(
                "SELECT count(*) FROM product GROUP BY product_type_id", Long.class);
        assertThat(perType).hasSize(10).containsOnly(10L);
    }

    /**
     * SKU does not exist in the legacy schema (ADR-0015), so every value here was invented by the
     * changelog: this checks they are unique and that they satisfy ck_product_sku rather than
     * merely happening to be accepted.
     */
    @Test
    @DisplayName("every product carries a unique, well-formed SKU and an image that exists")
    void everyProductIsUsableByTheCatalogueEndpoint() {
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT sku) FROM product", Long.class))
                .isEqualTo(100);

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM product WHERE sku !~ '^[A-Z0-9-]{3,32}$'", Long.class))
                .isZero();

        // image_key is nullable by design (RF-10), but this dataset gives every product a picture,
        // and each value names one of the files under resources/uploads/.
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM product WHERE image_key IS NULL OR image_key !~ '\\.jpg$'",
                        Long.class))
                .isZero();
    }

    @Test
    @DisplayName("there are four stores and exactly one of them is the online one")
    void seedsTheStoresWithASingleOnlineOne() {
        assertThat(countOf("store")).isEqualTo(4);

        assertThat(jdbc.queryForList("SELECT name FROM store WHERE is_online", String.class))
                .containsExactly("Ukun Online");
    }

    @Test
    @DisplayName("stock coverage decreases from the online store outwards")
    void seedsStockWithTheDecidedCoverage() {
        assertThat(countOf("store_stock")).isEqualTo(270);

        Map<String, Long> perStore = jdbc
                .queryForList(
                        """
                        SELECT s.name, count(*) AS lines
                          FROM store_stock ss
                          JOIN store s ON s.id = ss.store_id
                         GROUP BY s.name
                        """)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (String) row.get("name"), row -> (Long) row.get("lines")));

        assertThat(perStore).containsExactlyInAnyOrderEntriesOf(EXPECTED_STOCK_LINES_PER_STORE);
    }

    /**
     * The assertion this file exists for above the others. RF-01 treats "out of stock" and "not
     * sold here" as different answers, and the second one is invisible in the data unless some
     * product genuinely has no row for the online store.
     */
    @Test
    @DisplayName("both branches of RF-01 are present: out of stock, and no stock line at all")
    void representsBothAvailabilityBranches() {
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM store_stock ss
                          JOIN store s ON s.id = ss.store_id
                         WHERE s.is_online AND ss.quantity = 0
                        """,
                        Long.class))
                .isEqualTo(10);

        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM product p
                         WHERE NOT EXISTS (
                               SELECT 1 FROM store_stock ss
                                 JOIN store s ON s.id = ss.store_id
                                WHERE ss.product_id = p.id AND s.is_online)
                        """,
                        Long.class))
                .isEqualTo(10);
    }

    @Test
    @DisplayName("the two customers have an address and a masked card, and no order history")
    void seedsCustomersWithoutOrders() {
        assertThat(countOf("customer")).isEqualTo(2);
        assertThat(countOf("card")).isEqualTo(2);

        // NOT NULL in the schema, asserted here because a demo customer without them could not
        // reach checkout (RF-04, RF-06).
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM customer WHERE address_id IS NULL OR card_id IS NULL",
                        Long.class))
                .isZero();

        assertThat(countOf("customer_order")).isZero();
        assertThat(countOf("order_line")).isZero();
        assertThat(countOf("cart_item")).isZero();
        assertThat(countOf("idempotency_key")).isZero();
        assertThat(countOf("audit_log")).isZero();
    }

    @Test
    @DisplayName("there are twelve employees, one admin per store")
    void seedsEmployeesWithAnAdmin() {
        assertThat(countOf("employee")).isEqualTo(12);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM employee WHERE is_admin", Long.class))
                .isEqualTo(4);

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM employee WHERE is_admin AND email = 'salvador.oton@ukun.com'",
                        Long.class))
                .isEqualTo(1);
    }

    /**
     * ADR-0006: an account is a customer or an employee, never both. {@code ux_customer_account_id}
     * and {@code ux_employee_account_id} guard each table on its own; the overlap between them is
     * something only the data can get right.
     */
    @Test
    @DisplayName("no account is both a customer and an employee")
    void customerAndEmployeeAccountsAreDisjoint() {
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM customer c JOIN employee e ON e.account_id = c.account_id",
                        Long.class))
                .isZero();
    }

    @Test
    @DisplayName("the demo context applies the baseline and the seed data, and nothing else")
    void appliesBaselineAndDemoChangesets() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Integer.class))
                .isEqualTo(EXPECTED_CHANGESETS);
    }

    private long countOf(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
