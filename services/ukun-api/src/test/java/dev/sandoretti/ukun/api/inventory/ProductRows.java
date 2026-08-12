package dev.sandoretti.ukun.api.inventory;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The {@code product} rows an {@code inventory} fixture needs, inserted with SQL.
 *
 * <p><strong>SQL rather than the object mother of the {@code catalog} module, and that is the point
 * rather than a shortcut:</strong> {@code inventory} knows a product as a {@code Long} and nothing else
 * (ADR-0017 §2). The rows exist here only because {@code store_stock.product_id} has a foreign key to
 * satisfy, and building them through {@code catalog}'s entity would suggest this module has a view of a
 * product that it does not have.
 *
 * <p>Shared by the two suites of this package because both need the same row for the same reason:
 * {@code AvailabilityIT} for the endpoint of RF-01, {@code SellableStockIT} for the rule of ADR-0013.
 * The {@code is_active} column is a parameter and not a default precisely because that rule is the
 * difference between two otherwise identical rows.
 */
final class ProductRows {

    private ProductRows() {}

    /** A product of the {@code Sofás} type, which the caller is expected to have inserted. */
    static void insert(JdbcTemplate jdbc, String sku, String name, String price, boolean active) {
        jdbc.update(
                """
                INSERT INTO product (sku, name, description, price, product_type_id, is_active)
                     VALUES (?, ?, ?, CAST(? AS DECIMAL), (SELECT id FROM product_type WHERE name = 'Sofás'), ?)
                """,
                sku,
                name,
                name + ", for testing purposes.",
                price,
                active);
    }
}
