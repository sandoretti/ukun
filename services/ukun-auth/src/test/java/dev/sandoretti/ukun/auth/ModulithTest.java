package dev.sandoretti.ukun.auth;

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
 * Guard for the module graph of ADR-0017 §4. Pure bytecode analysis: no Spring context and no
 * Testcontainers, so it belongs to the {@code test} task.
 *
 * <p>ADR-0017 §4 is honest that the benefit is smaller here than in {@code ukun-api} — this service
 * is anaemic, and declaring modules is coherence of tooling more than a boundary that has to be
 * fought for. The edge it does enforce is the one that matters: {@code oauth2} may call
 * {@code accounts}, and only through {@code AccountsApi}. An injected {@code AccountRepository}, or a
 * customizer that reaches for the {@code Account} entity, fails here.
 */
class ModulithTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(UkunAuthApplication.class);

    /**
     * ADR-0017 §4, transcribed. {@code accounts} declares no list — unrestricted, not empty — because
     * it is the bottom of the graph. The other two declare {@code accounts}, and declaring a list at
     * all makes it <em>closed</em>: a future dependency has to be named, even on an open module
     * (ADR-0017 §1). This service has no {@code shared}, so nothing else appears.
     */
    private static final Map<String, Set<String>> EXPECTED_GRAPH =
            Map.of("accounts", Set.of(), "registration", Set.of("accounts"), "oauth2", Set.of("accounts"));

    @Test
    @DisplayName("no module reaches into another one's internals")
    void verifyModuleStructure() {
        MODULES.verify();
    }

    /**
     * {@code verify()} can only reject dependencies that exist in bytecode, so while
     * {@code registration} is still empty it would pass over a misspelled module name or an edge
     * nobody meant to declare. This compares the declared graph against the ADR directly.
     */
    @Test
    @DisplayName("the declared dependency graph is the one ADR-0017 §4 decided")
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
     * {@code ukun-api} has exactly one open module, {@code shared}. This service has none, and that
     * is not an omission: there is no cross-cutting exception hierarchy or advice here to justify
     * lifting the encapsulation of anything.
     */
    @Test
    @DisplayName("no module of ukun-auth is open")
    void noModuleIsOpen() {
        Set<String> open = MODULES.stream()
                .filter(ApplicationModule::isOpen)
                .map(ModulithTest::identifierOf)
                .collect(Collectors.toSet());

        assertThat(open).isEmpty();
    }

    @Test
    @DisplayName("the module diagram is generated instead of maintained by hand")
    void writeDocumentationSnippets() {
        new Documenter(MODULES).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }

    private static String identifierOf(ApplicationModule module) {
        return module.getIdentifier().toString();
    }
}
