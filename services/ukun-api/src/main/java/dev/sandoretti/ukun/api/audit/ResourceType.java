package dev.sandoretti.ukun.api.audit;

/**
 * The closed list of things this system audits, and the values of {@code audit_log.resource_type}
 * ({@code docs/11-observabilidad-y-logging.md} §2).
 *
 * <p>Six and not five: the comment in §2 originally listed only {@code product}, {@code store_stock},
 * {@code employee}, {@code customer} and {@code customer_order}, written before RF-13 existed — but §3
 * marks the three {@code product_type.*} events as audited, and renaming a type changes what the whole
 * public catalogue shows. The document has been corrected rather than this list trimmed.
 *
 * <p>The wire value is lower snake case, matching the table names it names, while the constant follows
 * Java. That mapping is the only reason {@link #value()} exists instead of {@link #name()}.
 */
public enum ResourceType {
    PRODUCT("product"),
    PRODUCT_TYPE("product_type"),
    STORE_STOCK("store_stock"),
    EMPLOYEE("employee"),
    CUSTOMER("customer"),
    CUSTOMER_ORDER("customer_order");

    private final String value;

    ResourceType(String value) {
        this.value = value;
    }

    /** What goes in the column — {@code VARCHAR(30)}, lower snake case. */
    public String value() {
        return value;
    }
}
