package dev.sandoretti.ukun.api.customers.application;

import dev.sandoretti.ukun.api.customers.domain.ProfileLocale;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code PUT /api/me/profile} — the {@code UpdateProfileRequest} schema of the contract
 * (RF-05): name, last name, address, card, favourite store and language.
 *
 * <p><strong>Neither the email nor the password are here</strong>, and their absence is the fix for a
 * real legacy bug rather than an omission. They are credentials and live in {@code ukun-auth}
 * (ADR-0004); the legacy edits them alongside the profile and validates email uniqueness without
 * excluding the editor, so saving a profile without touching the email could fail with "email already
 * exists" (RF-06, "do not carry over").
 *
 * <p><strong>It is a replacement, not a patch.</strong> {@code favoriteStoreId} absent or {@code null}
 * clears the favourite store — the contract does not mark it required and types it {@code [integer,
 * 'null']}, and a {@code PUT} that could only ever set and never unset would leave the customer no way
 * to undo the choice.
 *
 * <p>No hand-written {@code toString}: unlike {@link SignupRequest} this record carries no secret. The
 * card it holds is already masked, and there is no password in it to leak into a validation message.
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull ProfileLocale locale,
        @NotNull @Valid ProfileAddress address,
        @NotNull @Valid ProfileCard card,
        Long favoriteStoreId) {}
