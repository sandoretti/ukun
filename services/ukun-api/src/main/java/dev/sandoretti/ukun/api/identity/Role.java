package dev.sandoretti.ukun.api.identity;

/**
 * The business roles of RF-12, and the values of both the {@code roles} claim of the access token and
 * the {@code account_role} table of ukun_auth. The same three names the {@code Role} schema of the two
 * OpenAPI contracts declares.
 *
 * <p>There are only three because {@code admin} is a flag on an employee, not a level in a hierarchy
 * (RF-12), and because customer and employee are mutually exclusive (ADR-0006) — so {@code ADMIN}
 * only ever travels alongside {@code EMPLOYEE}.
 *
 * <p>This service does not own them: they are minted by ukun-auth and arrive in the token. What it
 * owns is the decision of what each one may do.
 */
public enum Role {
    CUSTOMER,
    EMPLOYEE,
    ADMIN
}
