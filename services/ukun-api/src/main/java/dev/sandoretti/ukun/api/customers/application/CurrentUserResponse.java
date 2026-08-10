package dev.sandoretti.ukun.api.customers.application;

import dev.sandoretti.ukun.api.customers.domain.ProfileLocale;
import dev.sandoretti.ukun.api.identity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * The body of {@code GET /api/me} — the {@code CurrentUser} schema of the contract. The session probe
 * the SPA calls right after login to learn who it is talking to and what it may show
 * ({@code docs/01-arquitectura.md} §2).
 *
 * <p>It serves customers and employees alike, unlike {@link CustomerProfileResponse}, and that is why
 * it carries an {@code accountId} and no {@code customerId}: the account is the one identifier both
 * kinds of person have. It is opaque to this service — the row it identifies lives in {@code ukun_auth}
 * ({@code docs/08-identidad-y-seguridad.md} §1).
 *
 * <p>{@code roles} is what the token says, not what this service decided. {@link Role} is imported from
 * {@code identity} rather than copied, which is exactly what a module's public API is for: a second
 * enum with the same three constants would be free to drift from the claim it is supposed to mirror.
 *
 * <p>{@code locale} is the persisted preference of ADR-0019, so it travels across devices. For an
 * employee it is the default value: {@code employee} has no such column, deliberately, and the
 * contract says as much.
 *
 * <p>The {@code @Schema(format)} is the contract's {@code format: email}, which springdoc cannot infer
 * from a {@code String} — and the SPA generates its type from it.
 */
public record CurrentUserResponse(
        UUID accountId, @Schema(format = "email") String email, List<Role> roles, ProfileLocale locale) {}
