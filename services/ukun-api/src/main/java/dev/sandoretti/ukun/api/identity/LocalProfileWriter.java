package dev.sandoretti.ukun.api.identity;

import java.util.UUID;

/**
 * Step 3 of the registration saga ({@code docs/08-identidad-y-seguridad.md} §3): the single local
 * transaction that writes the profile behind an account this service has just reserved.
 *
 * <p>It is a callback rather than a dependency of {@link AccountRegistrationService} because the
 * profile belongs to whoever owns it — {@code customer} to the {@code customers} module,
 * {@code employee} to {@code staff} — and this module must not know either of them. What
 * {@code identity} owns is the <em>order</em> of the saga and what to do when a step fails; what the
 * local step writes is none of its business.
 *
 * <p>The implementation is expected to be {@code @Transactional} and to be a bean of its own, so the
 * proxy actually applies: the orchestrator deliberately runs outside any transaction, and a local
 * write that inherited that would be no transaction at all.
 */
@FunctionalInterface
public interface LocalProfileWriter {

    /**
     * @param accountId the account ukun-auth just reserved, stored opaquely and without a foreign key
     *     because it lives in another database ({@code docs/08} §1)
     * @return the identifier of the row written, which the caller reports in its own business event
     */
    long write(UUID accountId);
}
