package dev.sandoretti.ukun.api.audit;

import org.slf4j.event.Level;

/**
 * The closed registry of {@code docs/11-observabilidad-y-logging.md} §3, restricted to the rows marked
 * with a tick in its "¿Auditoría?" column — the events that leave a row in {@code audit_log} on top of
 * their log line.
 *
 * <p><strong>This enum is what makes that column a mechanism instead of a promise.</strong> Before it,
 * "which events are audited" lived in a table in a document and in nobody's code: adding a use case
 * and forgetting its audit row broke nothing and showed up as a gap months later, when somebody asked
 * what had happened to a product. Now the two halves cannot come apart — emitting one of these events
 * goes through {@link AuditTrail#record}, which writes both, and there is no way to write a row for an
 * event that is not here. {@code AuditedEventRegistryTest} checks the list against §3 so the document
 * and this file cannot drift either.
 *
 * <p>Adding a value is the process §3 already describes for the registry itself, not a local decision.
 *
 * <p><strong>{@code profile.email.changed} is deliberately absent.</strong> §3 lists it with a tick,
 * but the operation does not exist: there is no email-change endpoint in
 * {@code docs/api/openapi/ukun-api.yaml} and nothing emits it. It is a row waiting for a use case, not
 * a gap in this enum — see the note §3 carries.
 *
 * <p>The three {@code WARN} levels are not a mistake either, and §3 says why: deleting a stock line, a
 * product or a product type is destructive and hard to undo without knowing the previous value, so it
 * stands out instead of dissolving among the INFOs.
 */
public enum AuditedEvent {

    /** The customer profile of a finished signup (RF-06). */
    SIGNUP_COMPLETED("signup.completed", Level.INFO, ResourceType.CUSTOMER),

    /** A confirmed order (RF-04, ADR-0016). */
    ORDER_CREATED("order.created", Level.INFO, ResourceType.CUSTOMER_ORDER),

    /** One line of stock created or edited — the same event for both (RF-09). */
    STOCK_UPDATED("stock.updated", Level.INFO, ResourceType.STORE_STOCK),

    /** The line removed, which is not the same as its quantity set to zero (RF-09). */
    STOCK_DELETED("stock.deleted", Level.WARN, ResourceType.STORE_STOCK),

    PRODUCT_CREATED("product.created", Level.INFO, ResourceType.PRODUCT),
    PRODUCT_UPDATED("product.updated", Level.INFO, ResourceType.PRODUCT),
    PRODUCT_DELETED("product.deleted", Level.WARN, ResourceType.PRODUCT),

    /** Out of the public catalogue, still in order history — reversible (ADR-0013). */
    PRODUCT_DISCONTINUED("product.discontinued", Level.INFO, ResourceType.PRODUCT),
    PRODUCT_REACTIVATED("product.reactivated", Level.INFO, ResourceType.PRODUCT),

    PRODUCT_TYPE_CREATED("product_type.created", Level.INFO, ResourceType.PRODUCT_TYPE),

    /** The one event that records a previous and a new value, because renaming a type changes what the whole public catalogue shows (RF-13). */
    PRODUCT_TYPE_UPDATED("product_type.updated", Level.INFO, ResourceType.PRODUCT_TYPE),
    PRODUCT_TYPE_DELETED("product_type.deleted", Level.WARN, ResourceType.PRODUCT_TYPE),

    /** The local half of the alta of RF-11, inside the saga's only transaction. */
    EMPLOYEE_CREATED("employee.created", Level.INFO, ResourceType.EMPLOYEE),

    /** Field names, never their values: a profile is personal data ({@code docs/11} §4). */
    PROFILE_UPDATED("profile.updated", Level.INFO, ResourceType.CUSTOMER);

    private final String eventName;
    private final Level level;
    private final ResourceType resourceType;

    AuditedEvent(String eventName, Level level, ResourceType resourceType) {
        this.eventName = eventName;
        this.level = level;
        this.resourceType = resourceType;
    }

    /** The name as {@code docs/11} §3 spells it, which is what goes in the log line. */
    public String eventName() {
        return eventName;
    }

    public Level level() {
        return level;
    }

    /**
     * The {@code action} column: this constant's own name.
     *
     * <p>Derived rather than declared as a fourth field because {@code docs/11} §2 gives the column
     * exactly these values ({@code STOCK_UPDATED}, {@code PRODUCT_DELETED}…) — a separate string would
     * be a second spelling of the same thing, free to disagree with the first.
     */
    public String action() {
        return name();
    }

    public ResourceType resourceType() {
        return resourceType;
    }
}
