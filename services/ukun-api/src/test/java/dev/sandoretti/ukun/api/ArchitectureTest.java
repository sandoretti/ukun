package dev.sandoretti.ukun.api;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import dev.sandoretti.ukun.archunit.HardRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The seven rules of {@code docs/03-analisis-y-patrones.md} §3, run against the production code of
 * {@code ukun-api}. Pure bytecode analysis — no Spring context, no container — so it belongs to
 * the {@code test} task, next to {@link ModulithTest}, and runs on every build.
 *
 * <p>The rules themselves live in {@code :ukun-test-support}, shared with {@code ukun-auth}; this
 * class is the service-specific half, and the only thing it decides is which package to import.
 * What proves the rules actually work is {@code ArchitectureRulesSelfTest}, which runs in that
 * project rather than in every service that applies them.
 *
 * <p>Each test calls {@code check} and lets ArchUnit's own {@code AssertionError} through: its
 * message lists the offending class, method and line, which no wrapper assertion would improve on.
 */
class ArchitectureTest {

    /**
     * {@code DoNotIncludeTests} is not decoration: the test sources of this module include classes
     * in {@code dev.sandoretti.ukun.api} ({@code Probe}, {@code ProbeMapper}) that exist to exercise
     * the annotation processor chain, not to obey the rules. The deliberately broken fixtures are no
     * longer a concern here — they moved to the test sources of {@code :ukun-test-support}, where
     * nothing on this classpath can reach them.
     */
    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("dev.sandoretti.ukun.api");

    @Test
    @DisplayName("no class in a web package reaches for jakarta.persistence")
    void webLayerIsFreeOfPersistence() {
        HardRules.WEB_LAYER_IS_FREE_OF_PERSISTENCE.check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("every entity writes its own equals, hashCode and toString")
    void entitiesWriteTheirOwnIdentity() {
        HardRules.ENTITIES_WRITE_THEIR_OWN_IDENTITY.check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("no @RestController depends on a repository")
    void controllersDoNotReachForRepositories() {
        HardRules.CONTROLLERS_DO_NOT_REACH_FOR_REPOSITORIES.check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("no public method of a @Service returns an entity")
    void servicesDoNotReturnEntities() {
        HardRules.SERVICES_DO_NOT_RETURN_ENTITIES.check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("no entity holds a Float or a Double")
    void entitiesDoNotUseFloatingPoint() {
        HardRules.ENTITIES_DO_NOT_USE_FLOATING_POINT.check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("every Request or Command carrying a secret masks it in toString")
    void dtosCarryingASecretMaskIt() {
        HardRules.DTOS_CARRYING_A_SECRET_MASK_IT.check(PRODUCTION_CLASSES);
    }

    @Test
    @DisplayName("no entity is ever passed to log.*(...)")
    void entitiesNeverReachTheLog() {
        HardRules.ENTITIES_NEVER_REACH_THE_LOG.check(PRODUCTION_CLASSES);
    }
}
