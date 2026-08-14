package dev.sandoretti.ukun.api.identity;

import java.util.UUID;

/**
 * What a registration left behind: the account, the local row written for it, and how far the saga
 * got.
 *
 * <p>{@code registrationId} travels back out because it is the correlation key of the whole flow —
 * the {@code Idempotency-Key} sent to ukun-auth and the field every {@code signup.*} event carries
 * ({@code docs/11-observabilidad-y-logging.md} §3). The caller emits the event that names its own row
 * ({@code signup.completed}, {@code employee.created}) and needs the same key on it, or the two halves
 * of one registration cannot be joined in the logs.
 */
public record Registration(UUID registrationId, UUID accountId, long localId, RegistrationOutcome outcome) {}
