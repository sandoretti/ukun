package dev.sandoretti.ukun.auth.domain;

/**
 * The closed list of business roles, ADR-0006.
 *
 * <p>{@code CUSTOMER} and {@code EMPLOYEE} are mutually exclusive — the saga refuses an email that
 * already holds the other one — and {@code ADMIN} is a sub-flag that only accompanies
 * {@code EMPLOYEE}. That is what lets the {@code roles} claim of the token be read without deciding
 * which "mode" a person is in (ADR-0006, docs/08-identidad-y-seguridad.md §4).
 *
 * <p>The same three values are enumerated in {@code ck_account_role_role} in the baseline. That is
 * not duplication for its own sake: docs/10 §1 wants integrity expressed in the changelog and not
 * only in Java, because the legacy is full of columns where the entity promises more than the
 * database enforces. Adding a fourth role means changing both, on purpose.
 */
public enum Role {
    CUSTOMER,
    EMPLOYEE,
    ADMIN
}
