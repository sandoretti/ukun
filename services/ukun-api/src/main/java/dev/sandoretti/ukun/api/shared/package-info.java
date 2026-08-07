/**
 * Cross-cutting building blocks every other module may use: the {@code UkunException} hierarchy, the
 * {@code @RestControllerAdvice} that renders them as ProblemDetail, the injected {@code Clock} and
 * common types. Owns no table.
 *
 * <p>Open module: its internals are visible to everyone by design, so it needs no
 * {@code allowedDependencies} of its own and nobody has to declare a dependency on it. See ADR-0017 §1.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package dev.sandoretti.ukun.api.shared;
