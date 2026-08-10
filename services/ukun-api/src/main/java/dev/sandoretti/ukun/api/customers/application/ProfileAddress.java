package dev.sandoretti.ukun.api.customers.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The {@code Address} schema of {@code docs/api/openapi/ukun-api.yaml}, as this module reads and
 * writes it: the postal address of a customer's profile.
 *
 * <p>One record for both directions, which is what {@code stores/application/AddressResponse} already
 * anticipated when it said the profile address "is written as well as read, and will carry its own
 * validation". It is a DTO of {@code customers} and not a shared type: two records with the same four
 * fields is the cheaper of the two mistakes available here, the other one being a shared DTO that two
 * modules pull in opposite directions.
 *
 * <p>One country for now, so it carries no country field — the contract says as much, and adding one
 * "just in case" would be a field the frontend has to render and nobody has decided the values of.
 *
 * <p>The constraints mirror the {@code CHECK}s of the baseline rather than replacing them
 * ({@code ck_address_street}, {@code ck_address_postal_code}). Bean Validation is what turns a bad
 * field into a {@code 400} naming it; the database constraint is what makes the rule true for every
 * writer, including one that forgets to validate. {@code docs/10-modelo-de-datos.md} §1 asks for both.
 */
public record ProfileAddress(
        @NotBlank @Size(max = 255) String street,
        @NotBlank @Size(max = 255) String city,
        @NotBlank @Size(max = 255) String province,
        @Pattern(regexp = "^[0-9]{5}$", message = "must be five digits") String postalCode) {}
