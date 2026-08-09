/**
 * Accounts: the credential itself. Owns {@code account} and {@code account_role}, the password hash,
 * and (from issue #25) the {@code /internal/accounts} endpoints the signup saga calls.
 *
 * <p>Declares no {@code allowedDependencies}, which means an unrestricted list rather than an empty
 * one — it is the bottom of this service's graph and has nobody to call (ADR-0017 §4).
 *
 * <p><strong>What is deliberately not here:</strong> {@code name} and {@code lastName}. They are
 * profile data and live in {@code ukun-api}, so that editing a profile stays a single local
 * transaction instead of a distributed write for a datum that is not a credential (ADR-0004). A
 * column of that shape landing in {@code account} is the ADR being reopened by accident.
 */
@org.springframework.modulith.ApplicationModule
package dev.sandoretti.ukun.auth.accounts;
