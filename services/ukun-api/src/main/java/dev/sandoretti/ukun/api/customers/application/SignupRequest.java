package dev.sandoretti.ukun.api.customers.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /api/signup} (RF-06): one form covering the account, the profile, the
 * address, the card and an optional favourite store.
 *
 * <p>It is a single request on purpose — the legacy asks for the same things across one form too, and
 * splitting it would turn one saga into several with nothing gained. What is <em>not</em> here is the
 * locale: {@code customer.locale} is defaulted by the column and edited later through
 * {@code /api/me/profile} (ADR-0019).
 *
 * <p>{@code favoriteStoreId} is nullable, and its existence is checked against {@code StoresApi}
 * before anything is written, so a wrong value comes back as a {@code 400} on that field rather than
 * as a foreign key violation ({@code docs/08-identidad-y-seguridad.md} §3, step 0).
 */
public record SignupRequest(
        @NotBlank @Email @Size(max = 255) String email,
        /*
         * @NotBlank only, no @Size. The length rules live in ukun-auth's PasswordPolicy, which is the
         * service that owns credentials and the only one that can also check the breach corpus. A
         * second declaration here would be a rule with two owners, and the copy that drifts is always
         * the one further from the decision.
         */
        @NotBlank String password,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull @Valid AddressRequest address,
        @NotNull @Valid CardRequest card,
        Long favoriteStoreId) {

    /**
     * Hand-written, and the reason is defect #10 rather than style: a record prints every component,
     * so the generated version would put a plaintext password into any log line or exception message
     * that touched this object — and the validation advice touches it on every rejected form.
     *
     * <p>{@code HardRules.DTOS_CARRYING_A_SECRET_MASK_IT} fails the build if this is deleted.
     * {@code docs/11-observabilidad-y-logging.md} §4 additionally forbids logging this body whole,
     * which is why nothing in the saga passes it to a logger even masked.
     */
    @Override
    public String toString() {
        return "SignupRequest[email=%s, password=***, name=%s, lastName=%s, favoriteStoreId=%s]"
                .formatted(email, name, lastName, favoriteStoreId);
    }
}
