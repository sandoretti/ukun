package dev.sandoretti.ukun.archunit.violations;

import org.springframework.web.bind.annotation.RestController;

/**
 * Breaks {@link dev.sandoretti.ukun.archunit.HardRules#CONTROLLERS_DO_NOT_REACH_FOR_REPOSITORIES}
 * on purpose: a controller wired straight to a repository skips the service layer, and with it the
 * transaction boundary and every rule that lives in the use case.
 */
@RestController
public class RepositoryDrivenController {

    private final LoggedEntityRepository repository;

    public RepositoryDrivenController(LoggedEntityRepository repository) {
        this.repository = repository;
    }

    public Object find(Long id) {
        return repository.findById(id);
    }
}
