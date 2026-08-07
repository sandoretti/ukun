package dev.sandoretti.ukun.api;

import lombok.Getter;
import lombok.Setter;

/**
 * Source type for {@link StackValidationIT}. Uses {@code @Getter}/{@code @Setter} rather than
 * {@code @Data} on purpose — {@code @Data} is banned on anything entity-shaped in this codebase
 * (see CLAUDE.md and docs/03-analisis-y-patrones.md).
 */
@Getter
@Setter
class Probe {

    private String name;
    private int quantity;
}
