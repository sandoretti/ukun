package dev.sandoretti.ukun.api.customers.application;

import dev.sandoretti.ukun.api.customers.domain.ProfileLocale;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The body of {@code GET}/{@code PUT /api/me/profile} — the {@code CustomerProfile} schema of the
 * contract (RF-05).
 *
 * <p><strong>{@code email} is a read projection and comes from the token's claim</strong>, not from the
 * column that mirrors it. The credential lives in {@code ukun-auth} and is changed there
 * ({@code docs/08-identidad-y-seguridad.md} §2, ADR-0004), so the claim is current by construction and
 * serving it costs no query and no write on the path of a {@code GET}. {@code name} and {@code lastName},
 * on the other hand, <em>are</em> profile data owned by this service, which is what makes editing a
 * profile a single local transaction.
 *
 * <p>No password, ever, in any direction: it is not a field of this schema, and the contract's own
 * header says no schema carries one in a response.
 *
 * <p>{@code favoriteStoreId} is referenced by identifier and not nested as a store: the {@code customers}
 * module does not navigate to the {@code stores} entity ({@code docs/14-modules.md} §2, ADR-0017 §2).
 * The frontend resolves the name with {@code GET /api/stores}. The {@code @Schema(nullable = true)} is
 * not decoration — springdoc cannot infer nullability from a reference type, and the contract types this
 * one {@code [integer, 'null']}; without it the SPA's generated type promises a value that may not be
 * there.
 */
public record CustomerProfileResponse(
        String name,
        String lastName,
        @Schema(format = "email") String email,
        ProfileLocale locale,
        ProfileAddress address,
        ProfileCard card,
        @Schema(nullable = true) Long favoriteStoreId) {}
