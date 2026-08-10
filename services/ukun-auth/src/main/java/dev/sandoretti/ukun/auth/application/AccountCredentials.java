package dev.sandoretti.ukun.auth.application;

import dev.sandoretti.ukun.auth.domain.Role;
import java.util.Set;
import java.util.UUID;

/**
 * What this service knows about an account, and the only shape in which it leaves the service
 * layer: a record, never the {@code Account} entity. That is not a convention — the fourth of the
 * seven ArchUnit rules of {@code docs/03-analisis-y-patrones.md} §3 fails the build otherwise.
 *
 * @param accountId the value that becomes the {@code sub} claim and that {@code ukun-api} stores as
 *     {@code customer.account_id} — opaque on the other side, with no real foreign key, because it
 *     crosses databases (docs/08-identidad-y-seguridad.md §1)
 * @param email the only credential of identity that leaves this service, as the {@code email} claim
 * @param passwordHash never the password: the hash, and only so that the authentication provider can
 *     match against it
 * @param enabled false while an account is reserved and not yet activated (skill {@code spring-auth},
 *     rule 4)
 * @param roles one business role, plus the admin sub-flag for employees (ADR-0006)
 */
public record AccountCredentials(
        UUID accountId, String email, String passwordHash, boolean enabled, Set<Role> roles) {

    /**
     * Hand-written because a record prints every component otherwise, and one of them is a password
     * hash — {@code docs/11-observabilidad-y-logging.md} §4 forbids a hash in a log line as flatly as
     * it forbids the password itself. The ArchUnit rule that catches this shape only matches classes
     * named {@code *Request} or {@code *Command}, so nothing would have complained; that is a reason
     * to write it, not a reason to skip it.
     */
    @Override
    public String toString() {
        return "AccountCredentials[accountId=%s, email=%s, enabled=%s, roles=%s, passwordHash=***]"
                .formatted(accountId, email, enabled, roles);
    }
}
