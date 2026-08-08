package dev.sandoretti.ukun.archunit;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The seven rules of {@code docs/03-analisis-y-patrones.md} §3, run against the production code of
 * {@code ukun-api}. Pure bytecode analysis — no Spring context, no container — so it belongs to
 * surefire, next to {@code ModulithTest}, and runs on every build.
 *
 * <p>Today it passes over almost nothing: the ten application modules are declared and empty, and
 * business code lands from v0.3 onwards. That is the point of writing the rules now rather than
 * later — the same reason {@code docs/14-modules.md} §4 gives for declaring the module graph while
 * it is still free to declare. What proves the rules actually work is
 * {@link ArchitectureRulesSelfTest}.
 *
 * <p>Each test calls {@code check} and lets ArchUnit's own {@code AssertionError} through: its
 * message lists the offending class, method and line, which no wrapper assertion would improve on.
 */
class ArchitectureTest {

    /**
     * {@code DoNotIncludeTests} is not decoration. The test sources of this module include classes
     * in {@code dev.sandoretti.ukun.api} ({@code Probe}, {@code ProbeMapper}) and the deliberately
     * broken fixtures under {@code dev.sandoretti.ukun.archunit.violations} — the rules are about
     * production code, and importing either would make this test fail for the wrong reason.
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
