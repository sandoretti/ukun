package dev.sandoretti.ukun.archunit.violations.web;

import jakarta.persistence.EntityManager;
import org.springframework.web.bind.annotation.RestController;

/**
 * Breaks {@link dev.sandoretti.ukun.archunit.HardRules#WEB_LAYER_IS_FREE_OF_PERSISTENCE} on
 * purpose. The package matters as much as the code: the rule is about anything under a {@code web}
 * package, which is where the skill {@code spring-api} puts the controllers of a module.
 */
@RestController
public class PersistenceAwareController {

    private final EntityManager entityManager;

    public PersistenceAwareController(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Object find(Long id) {
        return entityManager.find(Object.class, id);
    }
}
