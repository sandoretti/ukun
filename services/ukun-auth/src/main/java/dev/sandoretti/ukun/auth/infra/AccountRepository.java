package dev.sandoretti.ukun.auth.infra;

import dev.sandoretti.ukun.auth.domain.Account;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence of accounts. Injected by {@code AccountsService} and by nothing else. */
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

    /**
     * The reconciliation of rule 7 of the {@code spring-auth} skill: an account still disabled long
     * after being reserved is an abandoned signup, and deleting it frees the email.
     *
     * <p>A derived delete, which loads the rows and removes them one by one, rather than a bulk
     * {@code @Modifying} statement. Over a table of hundreds of rows once a day the difference is
     * nothing, and loading them is what lets Hibernate delete {@code account_role} itself instead of
     * this working only because the database happens to cascade — the cascade is the second belt
     * (baseline, changeset {@code 001-10}), not the mechanism.
     *
     * @return how many were collected, for the job's log line
     */
    long deleteByEnabledFalseAndCreatedAtBefore(Instant threshold);
}
