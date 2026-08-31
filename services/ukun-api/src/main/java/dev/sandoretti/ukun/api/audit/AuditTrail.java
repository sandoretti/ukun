package dev.sandoretti.ukun.api.audit;

import java.util.Map;

/**
 * Writes one audited business event: the log line <strong>and</strong> the {@code audit_log} row, in a
 * single call.
 *
 * <p><strong>One call and not two, on purpose.</strong> The alternative —{@code BusinessEvents.emit}
 * next to an {@code audit.record}— leaves the tick in {@code docs/11-observabilidad-y-logging.md} §3 as
 * something a person has to remember at every new use case, and a forgotten second line is invisible
 * until somebody needs the trail and finds a hole in it. Here the two halves are the same statement:
 * an event in {@link AuditedEvent} always produces both, and an event that is not in it goes through
 * {@code BusinessEvents} as before.
 *
 * <p><strong>The row goes in the caller's transaction</strong>, which is the guarantee ADR-0014 exists
 * for: a change that rolls back leaves no audit of having happened. That is why this interface takes no
 * transaction of its own and every caller invokes it from inside one — the single exception being
 * {@code SignupService}, whose saga has no ambient transaction to join and whose profile has already
 * committed by the time it calls (see the note there).
 *
 * @see AuditedEvent the closed list of what may be written
 */
public interface AuditTrail {

    /**
     * The normal case: the actor is whoever the request's token names.
     *
     * @param resourceId the identity of the thing that changed. Any type — it is stored as text so one
     *     {@code VARCHAR(64)} column serves both numeric ids and UUIDs ({@code docs/11} §2)
     * @param fields the same map the log line carries. Field <em>names</em> only for anything personal
     *     ({@code docs/11} §4): never a password, a token, a PAN, a CVV, or the value of an address
     */
    void record(AuditedEvent event, Object resourceId, Map<String, Object> fields);

    /**
     * The actor stated explicitly, for the one flow that has no token to read: the public signup, where
     * the actor is the account just created rather than a caller.
     */
    void record(AuditedEvent event, Actor actor, Object resourceId, Map<String, Object> fields);
}
