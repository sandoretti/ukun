/**
 * Cross-cutting building blocks every other module may use: the {@code UkunException} hierarchy, the
 * {@code @RestControllerAdvice} that renders them as ProblemDetail, the injected {@code Clock} and
 * common types. Owns no table.
 *
 * <p>Open module: its internals are visible to everyone by design — a module may import
 * {@code shared.web.PageResponse} without it being published through a named interface, and
 * {@code shared} itself declares no {@code allowedDependencies}.
 *
 * <p><strong>Being open does not exempt anyone from declaring the dependency.</strong> Verified
 * against Modulith 2.1.0 while writing the first module with code (v0.3): {@code type = OPEN} lifts
 * the <em>encapsulation</em> of the subpackages, but a module that declares
 * {@code allowedDependencies} declares a <em>closed</em> list, and any target outside it is a
 * violation — {@code shared} included. So the six modules with a declared list name {@code shared} in
 * it. See ADR-0017 §1 and docs/14-modules.md §1.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN
)
package dev.sandoretti.ukun.api.shared;
