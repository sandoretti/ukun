package dev.sandoretti.ukun.api.inventory;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Employees, seeded straight through JDBC — the same shape as {@link ProductRows} and for a stronger
 * version of the same reason.
 *
 * <p>{@code employee} belongs to the {@code staff} module, which has no entity yet (issue #45), so
 * there is no object mother to build one with and nothing to persist through the {@code EntityManager}.
 * That mirrors production: {@code EmployeeStoreRepository} reads this table natively for exactly the
 * same reason, and its javadoc says what replaces both when {@code staff} arrives.
 *
 * <p>The {@code account_id} is the whole point of every row seeded here: it is what a token's
 * {@code sub} claim is resolved against ({@code docs/08-identidad-y-seguridad.md} §4), so a test that
 * seeds an employee and mints a token for a different UUID is testing the {@code 403}.
 */
final class EmployeeRows {

    private EmployeeRows() {}

    static void insert(JdbcTemplate jdbc, UUID accountId, String name, long storeId, boolean admin) {
        jdbc.update(
                """
                INSERT INTO employee (account_id, name, last_name, email, job_title, is_admin, store_id)
                     VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                accountId,
                name,
                "de Prueba",
                name.toLowerCase() + "@ukun.com",
                "Dependiente",
                admin,
                storeId);
    }
}
