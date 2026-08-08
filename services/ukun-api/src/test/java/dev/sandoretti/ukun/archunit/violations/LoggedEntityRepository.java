package dev.sandoretti.ukun.archunit.violations;

import java.util.Optional;
import org.springframework.data.repository.Repository;

/** The repository {@link RepositoryDrivenController} reaches for. Not a violation by itself. */
public interface LoggedEntityRepository extends Repository<LoggedEntity, Long> {

    Optional<LoggedEntity> findById(Long id);
}
