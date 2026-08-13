package dev.sandoretti.ukun.api.catalog.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creating or renaming a product type (RF-13). One field, because a type has nothing else — and that
 * is the point of ADR-0013: it carries no {@code is_active}, so there is no third state to edit.
 *
 * <p>{@code @NotBlank} on top of {@code @Size(min = 1)}, which is the one place this service adds a
 * check the published contract does not spell out: {@code minLength: 1} lets a single space through,
 * and a type named {@code " "} is indistinguishable on screen from one with no name at all. The
 * trimming itself belongs to the use case, next to the uniqueness check it feeds.
 */
public record ProductTypeRequest(@NotBlank @Size(min = 1, max = 255) String name) {}
