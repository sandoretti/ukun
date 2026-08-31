package dev.sandoretti.ukun.api.audit;

/**
 * Who a row of the trail is attributed to: the account, and the role it held when it acted.
 *
 * <p>It exists as a parameter because one audited operation has no token to read it from —
 * {@code POST /api/signup} is {@code permitAll}, and the actor of {@code signup.completed} is the
 * account the saga has just created. Every other call resolves it from the request's own token, and
 * does not pass one.
 *
 * <p>{@code accountId} is text, not a {@code UUID}, for the reason {@code CurrentActor} already gives:
 * parsing is a way of rejecting a token, which is the resource server's job. The column is
 * {@code UUID} and the conversion happens once, when the row is built.
 */
public record Actor(String accountId, String role) {}
