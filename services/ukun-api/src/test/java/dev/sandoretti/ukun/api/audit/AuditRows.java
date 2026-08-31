package dev.sandoretti.ukun.api.audit;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads {@code audit_log} straight through JDBC, for the suites that assert what an operation left
 * behind.
 *
 * <p>Through JDBC and not through {@code AuditEntryRepository}, for the reason {@code EmployeeRows}
 * gives about {@code employee}: the repository is internal to the {@code audit} module, and a test
 * that reaches for it from {@code inventory}'s or {@code catalog}'s package would cross in the fixture
 * the boundary the production code does not cross. What is being asserted is the row, not the mapping.
 *
 * <p>{@link #clear} exists because the suites this helper serves truncate their own tables between
 * tests and not this one — {@code audit_log} is nobody's fixture. Clearing it in a
 * {@code @BeforeEach} of the audit test itself keeps the trail of one test out of the next, without
 * making six shared base classes know about a table only these tests read.
 */
public final class AuditRows {

    private static final ObjectMapper JSON = new ObjectMapper();

    private AuditRows() {}

    /**
     * One row of the trail. {@code occurredAt} is left out on purpose: it is filled by the database
     * default and no assertion here is about when.
     */
    public record Row(
            String action,
            String resourceType,
            String resourceId,
            UUID actorAccountId,
            String actorRole,
            String details,
            String traceId) {}

    /** Oldest first, so a test that provokes several reads them in the order it caused them. */
    public static List<Row> all(JdbcTemplate jdbc) {
        return jdbc.query(
                """
                SELECT action, resource_type, resource_id, actor_account_id, actor_role, details::text, trace_id
                  FROM audit_log
                 ORDER BY id
                """,
                (rs, row) -> new Row(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getObject(4, UUID.class),
                        rs.getString(5),
                        rs.getString(6),
                        rs.getString(7)));
    }

    /**
     * The single row the operation under test was expected to leave.
     *
     * @throws AssertionError if there is any number of rows other than one — which is the failure worth
     *     naming, because both "none" and "two" are real bugs of this feature and a {@code get(0)}
     *     would hide the second
     */
    public static Row only(JdbcTemplate jdbc) {
        List<Row> rows = all(jdbc);
        if (rows.size() != 1) {
            throw new AssertionError("Se esperaba exactamente una fila de auditoría, hay " + rows.size() + ": " + rows);
        }

        return rows.getFirst();
    }

    public static long count(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log", Long.class);
    }

    public static void clear(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE audit_log RESTART IDENTITY");
    }

    /**
     * {@code details}, parsed.
     *
     * <p>Asserting on the raw text does not work and the reason is worth knowing: {@code JSONB} does not
     * store the bytes it was given, it stores a parsed value and prints it back in its own normalised
     * form — {@code {"after": 10}}, with the space, and with the keys in whatever order Postgres finds
     * cheapest. A test that matched the string this service wrote would pass only by luck.
     *
     * <p>{@link Row#details()} still hands back the text, because the one assertion that must see it
     * raw is the sweep for anything sensitive: it looks for what should never be anywhere in the field,
     * and parsing first would let a secret hide in a key.
     */
    public static Map<String, Object> detailsOf(Row row) {
        return JSON.readValue(row.details(), new TypeReference<Map<String, Object>>() {});
    }
}
