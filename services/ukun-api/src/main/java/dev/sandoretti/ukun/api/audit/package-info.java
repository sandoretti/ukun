/**
 * The audit trail of ADR-0014: who did what to which resource, and when. Owns {@code audit_log}.
 *
 * <p><strong>Why a module of its own rather than more classes in {@code shared}.</strong> ADR-0017 §1
 * gives every module a "tables it owns" column, and {@code shared} is declared there —and in its own
 * {@code package-info}— as owning none. That is not bookkeeping: {@code shared} is the one
 * {@code type = OPEN} module, so anything inside it is importable from everywhere without declaring
 * anything. An {@code @Entity} living there would be an audit row that any module could write by
 * hand, which is the opposite of what a trail is for. Here the entity sits in {@code domain/} and the
 * only thing that leaves the module is {@link AuditTrail} and {@link AuditedEvent} — the golden rule
 * of {@code docs/14-modules.md} §2 applied to the module that most needs it.
 *
 * <p>It declares <strong>no</strong> {@code allowedDependencies} — an unrestricted list, like
 * {@code stores}, {@code storage} and {@code identity} — because it uses {@code shared} for the log
 * line ({@code BusinessEvents}), the actor ({@code CurrentActor}) and the trace ({@code TraceIds}).
 * It depends on nothing else, which is what keeps the graph acyclic with five modules pointing at it:
 * {@code shared} → {@code audit} → ({@code catalog}, {@code inventory}, {@code customers},
 * {@code staff}) → … → {@code orders}.
 */
package dev.sandoretti.ukun.api.audit;
