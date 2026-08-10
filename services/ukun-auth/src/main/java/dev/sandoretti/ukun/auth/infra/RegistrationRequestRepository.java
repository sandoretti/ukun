package dev.sandoretti.ukun.auth.infra;

import dev.sandoretti.ukun.auth.domain.RegistrationRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence of the idempotency keys of the signup saga. Injected by {@code AccountsService} and by
 * nothing else.
 *
 * <p>It declares no query of its own: the only look-up is by primary key, which {@link JpaRepository}
 * already provides. That is not a coincidence but the design — the key <em>is</em> the identifier
 * (docs/08 §3).
 */
public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, UUID> {}
