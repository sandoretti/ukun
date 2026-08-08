package dev.sandoretti.ukun.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Guard for the module graph of ADR-0017 §1. It runs from the very first commit of the skeleton, while
 * the modules are still empty, because that is the only moment at which the graph can be declared
 * without negotiating it against code that already violates it (docs/14-modules.md §4).
 *
 * <p>Pure bytecode analysis: no Spring context and no Testcontainers, so it belongs to surefire.
 */
class ModulithTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(UkunApiApplication.class);

    /**
     * ADR-0017 §1, transcribed. Topological order: shared -> (catalog, stores, storage, identity) ->
     * (inventory, customers, staff) -> cart -> orders.
     *
     * <p>Every module that declares a list names {@code shared} in it. That is not redundancy against
     * {@code shared} being open: {@code type = OPEN} lifts the encapsulation of its subpackages, but a
     * declared {@code allowedDependencies} is a <em>closed</em> list and anything outside it is a
     * violation. {@code stores}, {@code storage} and {@code identity} declare no list at all — an
     * unrestricted one — which is why they stay empty here.
     */
    private static final Map<String, Set<String>> EXPECTED_GRAPH = Map.of(
            "shared", Set.of(),
            "catalog", Set.of("storage", "shared"),
            "stores", Set.of(),
            "storage", Set.of(),
            "identity", Set.of(),
            "inventory", Set.of("catalog", "stores", "shared"),
            "customers", Set.of("stores", "identity", "shared"),
            "staff", Set.of("stores", "identity", "shared"),
            "cart", Set.of("catalog", "inventory", "customers", "shared"),
            "orders", Set.of("cart", "inventory", "customers", "catalog", "shared"));

    @Test
    @DisplayName("no module reaches into another one's internals")
    void verifyModuleStructure() {
        MODULES.verify();
    }

    /**
     * While the modules are empty there are no type dependencies for {@link ApplicationModules#verify()}
     * to reject, so it would pass over a misspelled module name or an edge nobody meant to declare. This
     * test compares the declared graph against ADR-0017 §1 directly, and is the one that fails today.
     */
    @Test
    @DisplayName("the declared dependency graph is the one ADR-0017 decided")
    void declaredGraphMatchesTheAdr() {
        Map<String, Set<String>> declared = MODULES.stream()
                .collect(Collectors.toMap(
                        ModulithTest::identifierOf,
                        module -> module.getAllowedDependencies(MODULES).stream()
                                .map(dependency -> identifierOf(dependency.getTargetModule()))
                                .collect(Collectors.toSet()),
                        (first, second) -> first,
                        TreeMap::new));

        assertThat(declared).containsExactlyInAnyOrderEntriesOf(EXPECTED_GRAPH);
    }

    /**
     * {@code shared} is the only open module: everyone may use its internals without declaring it, and
     * that is deliberate — it holds the exception hierarchy, the advice and the Clock (ADR-0017 §1).
     */
    @Test
    @DisplayName("shared is the only open module")
    void sharedIsTheOnlyOpenModule() {
        Set<String> open = MODULES.stream()
                .filter(ApplicationModule::isOpen)
                .map(ModulithTest::identifierOf)
                .collect(Collectors.toSet());

        assertThat(open).containsExactly("shared");
    }

    @Test
    @DisplayName("the module diagram is generated instead of maintained by hand")
    void writeDocumentationSnippets() {
        new Documenter(MODULES)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }

    private static String identifierOf(ApplicationModule module) {
        return module.getIdentifier().toString();
    }
}
