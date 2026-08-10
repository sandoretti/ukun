package dev.sandoretti.ukun.auth.application;

import java.util.UUID;

/**
 * The outcome of a reservation: which account holds the email, and whether this call is the one that
 * created it.
 *
 * <p>{@code created} is the difference between the {@code 201} and the {@code 200} of
 * {@code POST /internal/accounts}. Both are successes and ukun-api treats them alike; the flag is
 * what lets the controller answer the status the contract declares without asking again.
 *
 * <p>A record and not the {@link dev.sandoretti.ukun.auth.domain.Account} entity, for the reason
 * {@link AccountsService} states about {@link AccountCredentials}: the fourth ArchUnit rule fails the
 * build if a public service method returns an {@code @Entity}, and here that rule is also what keeps a
 * password hash from travelling to a controller that has no use for it.
 */
public record ReservedAccount(UUID accountId, boolean created) {}
