package dev.sandoretti.ukun.archunit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The test that closes issue #11. Its criterion is *«el build falla si se introduce a propósito
 * cualquiera de esas violaciones»*, and while the ten application modules are still empty
 * {@link ArchitectureTest} cannot show that: seven rules passing over no classes look exactly like
 * seven rules that are wrong.
 *
 * <p>So each rule is run twice. Once against {@code violations/}, where a class was written for the
 * express purpose of breaking it, and the rule has to fail. Once against {@code compliant/}, where
 * the same shape is written the way this repo requires, and the rule has to stay quiet — a rule
 * that fires over correct code gets switched off, which is a slower way of not having it.
 *
 * <p>The assertions look at the failure message, not just at the fact that something failed: a rule
 * can fail for the wrong reason, and over a package full of deliberate violations that is easy to
 * miss.
 */
class ArchitectureRulesSelfTest {

    private static final JavaClasses VIOLATIONS =
            new ClassFileImporter().importPackages("dev.sandoretti.ukun.archunit.violations");

    private static final JavaClasses COMPLIANT =
            new ClassFileImporter().importPackages("dev.sandoretti.ukun.archunit.compliant");

    @Test
    @DisplayName("the web rule catches a controller that imports jakarta.persistence")
    void webLayerRuleCatchesPersistenceInAController() {
        assertThatThrownBy(() -> HardRules.WEB_LAYER_IS_FREE_OF_PERSISTENCE.check(VIOLATIONS))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PersistenceAwareController")
                .hasMessageContaining("jakarta.persistence");
    }

    /**
     * The rule that would be silently broken without {@code services/lombok.config}:
     * {@code lombok.Data} is a {@code SOURCE} annotation and simply is not in the class file. If
     * this test ever starts failing, check that file before touching the rule.
     */
    @Test
    @DisplayName("the identity rule catches an entity annotated with @Data")
    void identityRuleCatchesLombokData() {
        assertThatThrownBy(() -> HardRules.ENTITIES_WRITE_THEIR_OWN_IDENTITY.check(VIOLATIONS))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("LombokEntity")
                .hasMessageContaining("toString");
    }

    @Test
    @DisplayName("the controller rule catches a controller wired to a repository")
    void controllerRuleCatchesADirectRepositoryDependency() {
        assertThatThrownBy(() -> HardRules.CONTROLLERS_DO_NOT_REACH_FOR_REPOSITORIES.check(VIOLATIONS))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("RepositoryDrivenController")
                .hasMessageContaining("LoggedEntityRepository");
    }

    @Test
    @DisplayName("the service rule catches a public method returning an entity")
    void serviceRuleCatchesALeakedEntity() {
        assertThatThrownBy(() -> HardRules.SERVICES_DO_NOT_RETURN_ENTITIES.check(VIOLATIONS))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("findTheEntityItself");
    }

    @Test
    @DisplayName("the money rule catches both the Double and the primitive float")
    void moneyRuleCatchesEveryFloatingPointField() {
        assertThatThrownBy(() -> HardRules.ENTITIES_DO_NOT_USE_FLOATING_POINT.check(VIOLATIONS))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("price")
                .hasMessageContaining("discount");
    }

    @Test
    @DisplayName("the secret rule catches a record that leaves toString to the compiler")
    void secretRuleCatchesAnUnmaskedRecord() {
        assertThatThrownBy(() -> HardRules.DTOS_CARRYING_A_SECRET_MASK_IT.check(VIOLATIONS))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("SignupRequest");
    }

    /**
     * One fact, three shapes: the rule is only worth having if it understands all of them, and the
     * one in the middle — {@code log.info("saved {}", entity)} compiled into a varargs array — is
     * the one the legacy actually wrote.
     */
    @Test
    @DisplayName("the logging rule catches the entity as argument, inside varargs and concatenated")
    void loggingRuleCatchesEveryShapeOfLeak() {
        assertThatThrownBy(() -> HardRules.ENTITIES_NEVER_REACH_THE_LOG.check(VIOLATIONS))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("logsTheEntityAsASingleArgument")
                .hasMessageContaining("logsTheEntityInsideAVarargsArray")
                .hasMessageContaining("logsTheEntityByConcatenation");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hardRules")
    @DisplayName("no rule fires over code that already follows it")
    void noRuleFiresOverCompliantCode(ArchRule rule) {
        assertThatCode(() -> rule.check(COMPLIANT)).doesNotThrowAnyException();
    }

    private static Stream<ArchRule> hardRules() {
        return HardRules.ALL.stream();
    }
}
