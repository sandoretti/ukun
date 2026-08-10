package dev.sandoretti.ukun.archunit.violations;

/**
 * Breaks {@link dev.sandoretti.ukun.archunit.HardRules#DTOS_CARRYING_A_SECRET_MASK_IT} on purpose,
 * and does it by omission — which is exactly why the rule is worth having. There is nothing here to
 * notice in review: the compiler writes a {@code toString()} that prints every component, password
 * included, and the class looks perfectly ordinary ({@code docs/11-observabilidad-y-logging.md} §4).
 */
public record SignupRequest(String email, String password) {}
