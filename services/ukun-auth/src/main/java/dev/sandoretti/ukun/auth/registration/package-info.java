/**
 * Registration: the {@code ukun-auth} half of the signup saga — the reserve/activate/release
 * operations and the {@code registration_request} table that makes retrying an alta idempotent
 * (docs/08-identidad-y-seguridad.md §3).
 *
 * <p>Empty on purpose. The table is already in the baseline, because a baseline is complete per
 * database (docs/10 §8); the code that uses it is issue #25. The module is declared now for the
 * reason docs/14-modules.md §4 gives: the graph is only free to declare while there is no code to
 * negotiate it against.
 *
 * <p>Declaring {@code allowedDependencies} makes the list <strong>closed</strong>, so anything this
 * module needs later has to be named here — including a module that happens to be open. That is not
 * the same thing as {@code type = OPEN}, which is about what is <em>visible</em> (ADR-0017 §1).
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = "accounts")
package dev.sandoretti.ukun.auth.registration;
