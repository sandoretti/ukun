package dev.sandoretti.ukun.api.customers.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The {@code Address} schema of {@code docs/api/openapi/ukun-api.yaml}: a postal address as it arrives
 * from a form.
 *
 * <p>One country for now, so it carries no country field — the contract says as much, and adding one
 * "just in case" would be a field the frontend has to render and nobody has decided the values of.
 *
 * <p>The constraints mirror the {@code CHECK}s of the baseline rather than replacing them
 * ({@code ck_address_street}, {@code ck_address_postal_code}). Bean Validation is what turns a bad
 * field into a {@code 400} naming it; the database constraint is what makes the rule true for every
 * writer, including one that forgets to validate. {@code docs/10-modelo-de-datos.md} §1 asks for both.
 */
public record AddressRequest(
        @NotBlank @Size(max = 255) String street,
        @NotBlank @Size(max = 255) String city,
        @NotBlank @Size(max = 255) String province,
        @Pattern(regexp = "^[0-9]{5}$", message = "must be five digits") String postalCode) {}
