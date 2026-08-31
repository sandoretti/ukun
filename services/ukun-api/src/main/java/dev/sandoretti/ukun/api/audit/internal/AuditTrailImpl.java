package dev.sandoretti.ukun.api.audit.internal;

import dev.sandoretti.ukun.api.audit.Actor;
import dev.sandoretti.ukun.api.audit.AuditTrail;
import dev.sandoretti.ukun.api.audit.AuditedEvent;
import dev.sandoretti.ukun.api.audit.domain.AuditEntry;
import dev.sandoretti.ukun.api.audit.infra.AuditEntryRepository;
import dev.sandoretti.ukun.api.shared.observability.BusinessEvents;
import dev.sandoretti.ukun.api.shared.observability.CurrentActor;
import dev.sandoretti.ukun.api.shared.observability.TraceIds;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the log line and the row, in that order and in one transaction.
 *
 * <p><strong>{@code @Transactional} with the default propagation is the whole design</strong>, not a
 * default nobody thought about. {@code REQUIRED} joins the caller's transaction when there is one —
 * which is the case for thirteen of the fourteen audited events, and what gives ADR-0014 its guarantee
 * that a change rolled back leaves no audit of having happened. For the fourteenth,
 * {@code signup.completed}, there is no ambient transaction: the saga makes two HTTP calls and cannot
 * hold a connection across them, so this opens one of its own. That case is sound for a reason worth
 * writing down — by the time the event is emitted the profile has already committed, so the row can
 * only ever be <em>missing</em> (if this insert fails), never orphaned from a change that was undone.
 *
 * <p>The log line goes first so that a failure to write the row still leaves the event in the log,
 * where the {@code traceId} of both is the same and the loss is visible.
 */
@Component
class AuditTrailImpl implements AuditTrail {

    private final AuditEntryRepository entries;
    private final ObjectMapper json;

    AuditTrailImpl(AuditEntryRepository entries, ObjectMapper json) {
        this.entries = entries;
        this.json = json;
    }

    @Override
    @Transactional
    public void record(AuditedEvent event, Object resourceId, Map<String, Object> fields) {
        record(event, new Actor(CurrentActor.accountId(), CurrentActor.role()), resourceId, fields);
    }

    @Override
    @Transactional
    public void record(AuditedEvent event, Actor actor, Object resourceId, Map<String, Object> fields) {
        // Both columns are NOT NULL, and the failure without this check is a NullPointerException from
        // inside UUID.fromString — which says "name is null" and names neither the event nor the reason.
        // Every audited operation but the signup runs behind a token, so a null here means the call was
        // made from somewhere with no security context: a test, or an endpoint that lost its filter.
        if (actor == null || actor.accountId() == null || actor.role() == null) {
            throw new IllegalStateException(
                    "No hay actor para auditar " + event.eventName() + ": audit_log lo exige (ADR-0014)");
        }

        BusinessEvents.emit(event.level(), event.eventName(), fields);

        entries.save(new AuditEntry(
                UUID.fromString(actor.accountId()),
                actor.role(),
                event.action(),
                event.resourceType().value(),
                String.valueOf(resourceId),
                // The same fields the log line carries, so the two halves of one event cannot describe
                // it differently. What may go in them is docs/11 §4's problem, not this method's: names
                // of changed fields, never their values, for anything personal.
                json.writeValueAsString(fields),
                // Null when the request carries no trace, rather than the fresh identifier TraceIds
                // invents for an error response: a trace_id that leads nowhere is worse than none.
                TraceIds.currentOrNull()));
    }
}
