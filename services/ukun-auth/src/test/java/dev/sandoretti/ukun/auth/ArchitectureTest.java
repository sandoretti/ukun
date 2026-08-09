package dev.sandoretti.ukun.auth;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import dev.sandoretti.ukun.archunit.HardRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The seven rules of {@code docs/03-analisis-y-patrones.md} §3, run against the production code of
 * {@code ukun-auth}. The rules themselves live in {@code :ukun-test-support}, shared with
 * {@code ukun-api}; this class is the service-specific half and the only thing it decides is which
 * package to import.
 *
 * <p>Two of the seven earn their keep here more than anywhere else in the repo: this service is the
 * one holding password hashes, so an entity whose {@code toString} the compiler wrote, or an entity
 * handed to {@code log.*(...)}, leaks a credential rather than a product name. Those are defects
 * #10, #20a and #20b of the legacy, and all three come from the same cause.
 *
 * <p>Each test calls {@code check} and lets ArchUnit's own {@code AssertionError} through: its
 * message lists the offending class, method and line.
 */
class ArchitectureTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("dev.sandoretti.ukun.auth");

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
