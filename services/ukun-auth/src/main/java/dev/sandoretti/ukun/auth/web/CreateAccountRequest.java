package dev.sandoretti.ukun.auth.web;

import dev.sandoretti.ukun.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * The body of {@code POST /internal/accounts}: credentials and roles, nothing else.
 *
 * <p><strong>There is no name, no last name and no profile data</strong>, and that is ADR-0004 rather
 * than an omission. Those live in ukun-api's own database, so editing a profile stays a single local
 * transaction instead of a write across two databases.
 *
 * <p>{@code enabled} is part of the contract even though the saga always sends {@code false}. The
 * field says what the caller intends, and the service does not grant it: a reservation is created
 * disabled whatever this says (skill {@code spring-auth}, rule 4). It is here because the contract
 * declares it required, not because it is a switch.
 */
public record CreateAccountRequest(
        @NotBlank @Email @Size(max = 255) String email,
        /*
         * Only @NotBlank. The length bound lives in PasswordPolicy and nowhere else, even though the
         * contract documents `minLength: 12` and a @Size here would look like the obvious place for
         * it: two declarations of one rule drift, and the one that drifts silently is the annotation,
         * because the policy is what every non-HTTP caller goes through. One rule, one owner.
         */
        @NotBlank String password,
        @NotEmpty Set<Role> roles,
        boolean enabled) {

    /**
     * Hand-written on purpose, and load-bearing. A record prints every component unless told
     * otherwise, so the compiler's version would put a plaintext password into any log line,
     * exception message or debugger frame that touched this — which is defect #10 reappearing through
     * a different door ({@code docs/11-observabilidad-y-logging.md} §4). The advice sees this object
     * inside a validation failure, so the door is not hypothetical.
     *
     * <p>{@code HardRules.DTOS_CARRYING_A_SECRET_MASK_IT} fails the build if this method is deleted;
     * the rule is the safety net, this method is the fix.
     */
    @Override
    public String toString() {
        return "CreateAccountRequest[email=%s, password=***, roles=%s, enabled=%s]"
                .formatted(email, roles, enabled);
    }
}
