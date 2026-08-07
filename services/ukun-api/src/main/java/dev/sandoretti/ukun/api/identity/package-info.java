/**
 * Identity boundary: {@code AccountsPort} with its HTTP adapter to ukun-auth, plus the JWT principal.
 * Owns no table — accounts and credentials live in ukun_auth (ADR-0004).
 *
 * <p>The other external boundary of this service, alongside {@code storage} (ADR-0017 §1).
 */
@org.springframework.modulith.ApplicationModule
package dev.sandoretti.ukun.api.identity;
