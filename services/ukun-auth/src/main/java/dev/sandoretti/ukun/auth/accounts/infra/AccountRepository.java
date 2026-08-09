package dev.sandoretti.ukun.auth.accounts.infra;

import dev.sandoretti.ukun.auth.accounts.domain.Account;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence of {@code accounts}. Internal to the module: nothing outside it may inject this. */
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * The hot query of the whole service — every login goes through it. The {@code @EntityGraph} is
     * what turns "account plus its roles" into one statement instead of a second round trip per
     * account, which is the first rung of the reading ladder in
     * {@code docs/03-analisis-y-patrones.md} §2 and the fix for the family of defects around #12.
     *
     * <p>No {@code lower()} and no {@code UPPER}: the column is {@code CITEXT}.
     */
    @EntityGraph(attributePaths = "roles")
    Optional<Account> findByEmail(String email);
}
