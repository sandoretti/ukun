package dev.sandoretti.ukun.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The ukun_app baseline applied to an empty database, which is the criterion issue #9 states and the
 * fourth rule of the `liquibase` skill: a changeset is only proven against a virgin database, never
 * against a local one that has already evolved and can hide an assumed previous state.
 *
 * <p>It asserts the schema in two registers. Metadata — the tables and the named indexes are exactly
 * the ones docs/10-modelo-de-datos.md §4 and §8 decided, no more and no fewer — catches an index
 * added "just in case" as readily as a missing one. Behaviour — the invariants that exist precisely
 * because the legacy broke them: the shared address of defect #19, the single online store of defect
 * #6, and the orders with total 0 that RF-04 documents.
 *
 * <p>It keeps its own container instead of extending an {@code AbstractIntegrationTest}: that class
 * belongs to the Testcontainers/ArchUnit slice (issue #11) and does not exist yet. When it lands,
 * this test folds onto it and the two annotations below go away.
 */
@Testcontainers
@SpringBootTest
class SchemaBaselineIT {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    /** docs/10-modelo-de-datos.md §8. Thirteen, and the baseline creates all of them at once. */
    private static final List<String> EXPECTED_TABLES = List.of(
            "address",
            "audit_log",
            "card",
            "cart_item",
            "customer",
            "customer_order",
            "employee",
            "idempotency_key",
            "order_line",
            "product",
            "product_type",
            "store",
            "store_stock");

    /**
     * docs/10-modelo-de-datos.md §4, in full: the five backed by a UNIQUE constraint and the eleven
     * created explicitly. Primary keys are excluded — they are not a decision this list records.
     *
     * <p>That the assertion is {@code containsExactlyInAnyOrder} and not {@code contains} is the
     * point: §4 also lists what stays out and why, and the one most likely to creep in is an index
     * for name search, which has a written activation threshold instead.
     */
    private static final List<String> EXPECTED_INDEXES = List.of(
            "ux_product_type_name",
            "ux_store_name",
            "ux_product_sku",
            "ux_store_stock",
            "ux_idempotency_customer_key",
            "ux_customer_account_id",
            "ux_employee_account_id",
            "ux_store_online",
            "ix_customer_order_customer_placed",
            "ix_store_stock_store",
            "ix_cart_item_product",
            "ix_order_line_product",
            "ix_employee_store",
            "ix_product_type",
            "ix_audit_log_resource",
            "ix_audit_log_actor");

    /**
     * docs/10-modelo-de-datos.md §6, transcribed. CASCADE only where the child is not history —
     * the cart, the order's own lines, a store's stock; SET NULL for the optional favourite store;
     * RESTRICT everywhere else, because deleting a customer must not delete the sale (RF-10, RF-13).
     */
    private static final Map<String, String> EXPECTED_DELETE_RULES = Map.ofEntries(
            Map.entry("fk_store_address", "RESTRICT"),
            Map.entry("fk_product_product_type", "RESTRICT"),
            Map.entry("fk_customer_address", "RESTRICT"),
            Map.entry("fk_customer_card", "RESTRICT"),
            Map.entry("fk_customer_favorite_store", "SET NULL"),
            Map.entry("fk_employee_store", "RESTRICT"),
            Map.entry("fk_store_stock_product", "RESTRICT"),
            Map.entry("fk_store_stock_store", "CASCADE"),
            Map.entry("fk_cart_item_customer", "CASCADE"),
            Map.entry("fk_cart_item_product", "RESTRICT"),
            Map.entry("fk_customer_order_customer", "RESTRICT"),
            Map.entry("fk_order_line_order", "CASCADE"),
            Map.entry("fk_order_line_product", "RESTRICT"),
            Map.entry("fk_idempotency_key_customer", "RESTRICT"),
            Map.entry("fk_idempotency_key_order", "RESTRICT"));

    /**
     * How many {@code ck_*} constraints each table carries, per docs/10 §1 (every mandatory text is
     * non-blank) and §2 (the type-by-type table). Grouped by table rather than listed one by one:
     * it fails just as loudly when a CHECK goes missing, and it stays readable.
     */
    private static final Map<String, Long> EXPECTED_CHECKS_PER_TABLE = Map.ofEntries(
            Map.entry("address", 4L),
            Map.entry("card", 4L),
            Map.entry("product_type", 1L),
            Map.entry("store", 2L),
            Map.entry("product", 4L),
            Map.entry("customer", 4L),
            Map.entry("employee", 4L),
            Map.entry("store_stock", 1L),
            Map.entry("cart_item", 1L),
            Map.entry("customer_order", 10L),
            Map.entry("order_line", 5L),
            Map.entry("idempotency_key", 1L));

    private static final String CHANGE_LOG = "classpath:db/changelog/db.changelog-master.yaml";

    /** The nineteen changesets of {@code baseline/001-esquema-inicial.yaml}. */
    private static final int EXPECTED_CHANGESETS = 19;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("the baseline creates the thirteen tables of docs/10 §8 and nothing else")
    void createsEveryTableOfTheDataModel() {
        List<String> tables = jdbc.queryForList(
                """
                SELECT table_name FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_type = 'BASE TABLE'
                   AND table_name NOT LIKE 'databasechangelog%'
                """,
                String.class);

        assertThat(tables).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
    }

    @Test
    @DisplayName("the indexes are exactly the ones docs/10 §4 justifies, with their agreed names")
    void createsExactlyTheJustifiedIndexes() {
        List<String> indexes = jdbc.queryForList(
                """
                SELECT indexname FROM pg_indexes
                 WHERE schemaname = 'public'
                   AND indexname NOT LIKE 'pk_%'
                   AND tablename NOT LIKE 'databasechangelog%'
                """,
                String.class);

        assertThat(indexes).containsExactlyInAnyOrderElementsOf(EXPECTED_INDEXES);
    }

    /**
     * The one assertion this file exists for above the others. Liquibase's OSS {@code createTable}
     * accepts a {@code checkConstraint} attribute and then discards it: the changeset reports
     * success and the constraint is absent. So every CHECK in the baseline is written as raw SQL,
     * and this counts them back out of the database — a lost one is otherwise invisible until
     * production accepts a blank name or an order whose total is zero.
     */
    @Test
    @DisplayName("every CHECK of docs/10 §1 and §2 reached the database")
    void everyCheckConstraintExists() {
        Map<String, Long> checksPerTable = jdbc
                .queryForList(
                        """
                        SELECT tc.table_name
                          FROM information_schema.table_constraints tc
                         WHERE tc.constraint_type = 'CHECK'
                           AND tc.table_schema = 'public'
                           AND tc.constraint_name LIKE 'ck_%'
                        """)
                .stream()
                .collect(Collectors.groupingBy(row -> (String) row.get("table_name"), Collectors.counting()));

        assertThat(checksPerTable).containsExactlyInAnyOrderEntriesOf(EXPECTED_CHECKS_PER_TABLE);
    }

    /**
     * The three CHECKs that exist because the legacy broke exactly these: it creates orders with
     * total 0 (RF-04), declares {@code @NotNull @Min(0)} on a stock its database lets go negative,
     * and has no guard at all on the line total.
     */
    @Test
    @DisplayName("the invariants the legacy violated are enforced by the database itself")
    void theDatabaseRejectsWhatTheLegacyUsedToAccept() {
        long customerId = insertCustomer();

        assertThatThrownBy(() -> insertOrder(customerId, "0.00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_customer_order_total");

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO store_stock (product_id, store_id, quantity) VALUES (?, ?, -1)",
                        insertProduct(),
                        insertStore(insertAddress(), "Ukun Centro", false)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_store_stock_quantity");

        long orderId = insertOrder(customerId, "10.00");
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO order_line (order_id, product_id, product_name, unit_price, quantity, total_price) "
                                + "VALUES (?, ?, 'Sofá', 10.00, 2, 99.00)",
                        orderId,
                        insertProduct()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_order_line_total");
    }

    /**
     * The legacy {@code init.sql} has not a single {@code ON DELETE} clause, so all of its foreign
     * keys are {@code NO ACTION} — which happens to be right for most of them, by accident. This
     * asserts the decision was made rather than defaulted into: no key here is {@code NO ACTION}.
     */
    @Test
    @DisplayName("every foreign key carries the ON DELETE policy docs/10 §6 decided for it")
    void everyForeignKeyDeclaresItsDeletePolicy() {
        Map<String, String> rules = jdbc
                .queryForList(
                        """
                        SELECT tc.constraint_name, rc.delete_rule
                          FROM information_schema.table_constraints tc
                          JOIN information_schema.referential_constraints rc
                            ON rc.constraint_name = tc.constraint_name
                           AND rc.constraint_schema = tc.table_schema
                         WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public'
                        """)
                .stream()
                .collect(Collectors.toMap(
                        row -> (String) row.get("constraint_name"), row -> (String) row.get("delete_rule")));

        assertThat(rules).containsExactlyInAnyOrderEntriesOf(EXPECTED_DELETE_RULES);
    }

    @Test
    @DisplayName("an order keeps its own address and card columns, never a reference to the profile's")
    void ordersSnapshotTheAddressAndCardInsteadOfSharingThem() {
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'customer_order'",
                String.class);

        assertThat(columns)
                .contains(
                        "shipping_street",
                        "shipping_city",
                        "shipping_province",
                        "shipping_postal_code",
                        "payment_last4",
                        "payment_brand",
                        "payment_exp_month",
                        "payment_exp_year")
                .doesNotContain("address_id", "card_id");
    }

    @Test
    @DisplayName("a second online store is rejected by the database, not by a convention")
    void exactlyOneStoreCanBeOnline() {
        long addressId = insertAddress();
        insertStore(addressId, "Ukun Online", true);

        assertThatThrownBy(() -> insertStore(insertAddress(), "Ukun Online II", true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_store_online");
    }

    /**
     * The second run goes through a datasource of its own. {@link SpringLiquibase} closes the
     * datasource it migrated unless told otherwise, so reusing the context's pool here would leave
     * every later test in this class without a connection.
     */
    @Test
    @DisplayName("re-running the changelog against an up-to-date database applies nothing")
    void applyingTheChangelogTwiceIsANoOp() throws Exception {
        Integer before = jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Integer.class);

        SpringLiquibase secondRun = new SpringLiquibase();
        secondRun.setDataSource(new SimpleDriverDataSource(
                new Driver(), POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        secondRun.setChangeLog(CHANGE_LOG);
        secondRun.setResourceLoader(new DefaultResourceLoader());
        secondRun.afterPropertiesSet();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Integer.class))
                .isEqualTo(before)
                .isEqualTo(EXPECTED_CHANGESETS);
    }

    /** Ordered truncation rather than {@code @DirtiesContext}, per the `testing` skill. */
    @AfterEach
    void clearTheRowsThisTestWrote() {
        jdbc.execute("TRUNCATE TABLE " + String.join(", ", EXPECTED_TABLES) + " RESTART IDENTITY CASCADE");
    }

    private long insertAddress() {
        return jdbc.queryForObject(
                "INSERT INTO address (street, city, province, postal_code) "
                        + "VALUES ('Calle Mayor 1', 'Madrid', 'Madrid', '28001') RETURNING id",
                Long.class);
    }

    private long insertStore(long addressId, String name, boolean online) {
        return jdbc.queryForObject(
                "INSERT INTO store (name, phone, is_online, address_id) "
                        + "VALUES (?, '+34910000000', ?, ?) RETURNING id",
                Long.class,
                name,
                online,
                addressId);
    }

    private long insertCustomer() {
        Long cardId = jdbc.queryForObject(
                "INSERT INTO card (last4, brand, exp_month, exp_year) "
                        + "VALUES ('4242', 'VISA', 12, 2030) RETURNING id",
                Long.class);

        return jdbc.queryForObject(
                "INSERT INTO customer (account_id, name, last_name, email, address_id, card_id) "
                        + "VALUES (gen_random_uuid(), 'Ada', 'Lovelace', 'ada@ukun.dev', ?, ?) RETURNING id",
                Long.class,
                insertAddress(),
                cardId);
    }

    private long insertProduct() {
        Long typeId = jdbc.queryForObject(
                "INSERT INTO product_type (name) VALUES ('Sofás-' || gen_random_uuid()) RETURNING id", Long.class);

        return jdbc.queryForObject(
                "INSERT INTO product (sku, name, description, price, product_type_id) "
                        + "VALUES (upper(replace(gen_random_uuid()::text, '-', ''))::varchar(12), "
                        + "'Sofá de cuero', 'Tres plazas', 399.99, ?) RETURNING id",
                Long.class,
                typeId);
    }

    private long insertOrder(long customerId, String total) {
        return jdbc.queryForObject(
                """
                INSERT INTO customer_order (
                    customer_id, placed_at, total,
                    shipping_street, shipping_city, shipping_province, shipping_postal_code,
                    payment_last4, payment_brand, payment_exp_month, payment_exp_year)
                VALUES (?, now(), ?::decimal,
                    'Calle Mayor 1', 'Madrid', 'Madrid', '28001',
                    '4242', 'VISA', 12, 2030)
                RETURNING id
                """,
                Long.class,
                customerId,
                total);
    }
}
