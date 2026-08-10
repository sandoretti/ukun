package dev.sandoretti.ukun.api.identity;

import java.util.UUID;

/**
 * The account ukun-auth reserved, and whether this call is the one that created it.
 *
 * <p>{@code created} is the difference between the {@code 201} and the {@code 200} of
 * {@code POST /internal/accounts}: the same {@code Idempotency-Key} replayed answers {@code 200} with
 * the <em>same</em> {@code accountId} and creates nothing. Callers that treat both alike are correct;
 * the flag is here so the saga can tell a first attempt from a retry without asking again.
 */
public record AccountRef(UUID accountId, boolean created) {}
