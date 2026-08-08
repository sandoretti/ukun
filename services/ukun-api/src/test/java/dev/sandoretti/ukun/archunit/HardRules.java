package dev.sandoretti.ukun.archunit;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import java.lang.classfile.MethodModel;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The seven rules of {@code docs/03-analisis-y-patrones.md} §3, transcribed one by one. Each is a
 * defect of the legacy that the new code must not reproduce, and each names its source.
 *
 * <p>There is no eighth rule here. "Feature packages do not import each other except through
 * {@code ..api..}" is the one ADR-0017 replaced with {@code ApplicationModules.verify()}, which
 * also catches dependency cycles that ArchUnit never did; it lives in {@code ModulithTest}. The
 * other seven stay, because Modulith knows nothing about {@code @Data} on an entity, {@code Double}
 * for money, or an entity handed to {@code log.*(...)}.
 *
 * <p>Every rule allows an empty {@code should} on purpose. Today the ten application modules are
 * declared and empty, so all seven pass over nothing — the proof that they work is
 * {@code ArchitectureRulesSelfTest}, which runs each of them against a class written to break it.
 */
final class HardRules {

    private static final String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";
    private static final String SERVICE = "org.springframework.stereotype.Service";
    private static final String SPRING_DATA_REPOSITORY = "org.springframework.data.repository.Repository";
    private static final String SPRING_REPOSITORY = "org.springframework.stereotype.Repository";

    /** Lombok writes all four; an entity is expected to write the first three by hand. */
    private static final Set<String> IDENTITY_METHODS = Set.of("equals", "hashCode", "toString", "canEqual");

    /** {@code docs/11-observabilidad-y-logging.md} §4, verbatim. */
    private static final Pattern SECRET_FIELD = Pattern.compile("(?i).*(password|token|cvv|number|pan).*");

    private HardRules() {}

    /**
     * A class in a web package that reaches for {@code jakarta.persistence} is a controller doing
     * persistence, which is the shape the layered structure of the skill {@code spring-api} exists
     * to prevent.
     */
    static final ArchRule WEB_LAYER_IS_FREE_OF_PERSISTENCE = noClasses()
            .that()
            .resideInAPackage("..web..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("jakarta.persistence..")
            .because("a @RestController that touches JPA has skipped the service layer (docs/03 §3)")
            .allowEmptyShould(true);

    /**
     * "Ninguna @Entity está anotada con @Data". Not literally checkable: {@code lombok.Data} has
     * {@code SOURCE} retention and is gone before a class file exists. What survives is
     * {@code @lombok.Generated} on the methods it wrote — see {@code services/lombok.config} — so
     * the rule is stated as what {@code docs/03} §2 actually asks for: an entity writes its own
     * {@code equals}/{@code hashCode} by id and a {@code toString} with scalars only.
     *
     * <p>Two separate reasons, both real in this repo: {@code @Data} caused a {@code StackOverflowError}
     * between {@code Pedido} and {@code ProductoPedido} through mutual {@code toString}, and a
     * generated {@code toString} leaks passwords and card numbers into the log.
     */
    static final ArchRule ENTITIES_WRITE_THEIR_OWN_IDENTITY = classes()
            .that()
            .areAnnotatedWith(Entity.class)
            .should(notLetLombokWriteTheirIdentityMethods())
            .because("@Data on an entity means a recursive toString and a leaked secret (docs/03 §2)")
            .allowEmptyShould(true);

    static final ArchRule CONTROLLERS_DO_NOT_REACH_FOR_REPOSITORIES = noClasses()
            .that()
            .areAnnotatedWith(REST_CONTROLLER)
            .should()
            .dependOnClassesThat(repositories())
            .because("a controller talks to a service, never to a repository (docs/03 §3)")
            .allowEmptyShould(true);

    /**
     * ArchUnit compares the raw return type, so a {@code List<Order>} or an {@code Optional<Order>}
     * slips past erasure. The rule is still worth having for the direct case, and the boundary that
     * matters most between modules is covered structurally instead: {@code docs/14-modules.md} §2
     * forbids an {@code @Entity} in the public API of a module, and {@code verify()} enforces that
     * nothing outside a module can even see its entities.
     */
    static final ArchRule SERVICES_DO_NOT_RETURN_ENTITIES = noMethods()
            .that()
            .arePublic()
            .and()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(SERVICE)
            .should()
            .haveRawReturnType(annotatedWith(Entity.class))
            .because("a service returns a DTO record mapped with MapStruct, never the entity (ADR-0009)")
            .allowEmptyShould(true);

    static final ArchRule ENTITIES_DO_NOT_USE_FLOATING_POINT = noFields()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(Entity.class)
            .should()
            .haveRawType(Float.class)
            .orShould()
            .haveRawType(Double.class)
            .orShould()
            .haveRawType(float.class)
            .orShould()
            .haveRawType(double.class)
            .because("money is BigDecimal; the legacy used Float and lost cents (docs/03 §2)")
            .allowEmptyShould(true);

    /**
     * A {@code record} without an explicit {@code toString()} prints every component, which for a
     * signup request means the password and for card data means the PAN and the CVV. That is the
     * counterpart of having chosen records for DTOs — {@code docs/11-observabilidad-y-logging.md} §4.
     */
    static final ArchRule DTOS_CARRYING_A_SECRET_MASK_IT = classes()
            .that(carriesASecret())
            .should(declareTheirOwnToString())
            .because("a record prints all its components unless told otherwise (docs/11 §4)")
            .allowEmptyShould(true);

    static final ArchRule ENTITIES_NEVER_REACH_THE_LOG = classes()
            .should(new EntityLoggingCondition())
            .because("an entity in a log line is how defects #10, #20a and #20b leaked secrets (docs/11 §4)")
            .allowEmptyShould(true);

    /** The seven, in the order {@code docs/03-analisis-y-patrones.md} §3 lists them. */
    static final List<ArchRule> ALL = List.of(
            WEB_LAYER_IS_FREE_OF_PERSISTENCE,
            ENTITIES_WRITE_THEIR_OWN_IDENTITY,
            CONTROLLERS_DO_NOT_REACH_FOR_REPOSITORIES,
            SERVICES_DO_NOT_RETURN_ENTITIES,
            ENTITIES_DO_NOT_USE_FLOATING_POINT,
            DTOS_CARRYING_A_SECRET_MASK_IT,
            ENTITIES_NEVER_REACH_THE_LOG);

    private static DescribedPredicate<JavaClass> repositories() {
        return assignableTo(SPRING_DATA_REPOSITORY)
                .or(annotatedWith(SPRING_REPOSITORY))
                .or(simpleNameEndingWith("Repository"))
                .as("repositories");
    }

    private static DescribedPredicate<JavaClass> carriesASecret() {
        return new DescribedPredicate<>("are a Request or Command with a password, token, cvv, number or pan field") {
            @Override
            public boolean test(JavaClass javaClass) {
                boolean isCommandShaped = javaClass.getSimpleName().endsWith("Request")
                        || javaClass.getSimpleName().endsWith("Command");

                return isCommandShaped
                        && javaClass.getFields().stream()
                                .anyMatch(field -> SECRET_FIELD.matcher(field.getName()).matches());
            }
        };
    }

    private static ArchCondition<JavaClass> declareTheirOwnToString() {
        return new ArchCondition<>("declare a toString() that masks it") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean handWritten = Bytecode.parse(javaClass)
                        .map(Bytecode::declaresHandWrittenToString)
                        .orElse(true);

                if (!handWritten) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass,
                            "%s carries a secret and leaves toString() to the compiler, which prints every component"
                                    .formatted(javaClass.getName())));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notLetLombokWriteTheirIdentityMethods() {
        return new ArchCondition<>("write equals, hashCode and toString by hand") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                Bytecode.parse(javaClass).ifPresent(model -> model.methods().stream()
                        .filter(HardRules::isAnIdentityMethod)
                        .filter(Bytecode::isLombokGenerated)
                        .forEach(method -> events.add(SimpleConditionEvent.violated(
                                javaClass,
                                "%s lets Lombok generate %s() — @Data and @EqualsAndHashCode are banned on entities"
                                        .formatted(
                                                javaClass.getName(),
                                                method.methodName().stringValue())))));
            }
        };
    }

    private static boolean isAnIdentityMethod(MethodModel method) {
        return IDENTITY_METHODS.contains(method.methodName().stringValue());
    }
}
