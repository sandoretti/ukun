package dev.sandoretti.ukun.api.identity.internal;

import dev.sandoretti.ukun.api.identity.CurrentAccount;
import dev.sandoretti.ukun.api.identity.IdentityApi;
import dev.sandoretti.ukun.api.identity.Role;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Reads the current account off the validated token. The only class in this service that knows the
 * name of a claim, which is the reason {@link IdentityApi} exists at all.
 *
 * <p>It reads the {@code roles} claim rather than the granted authorities. The two carry the same
 * information — Boot maps one onto the other through {@code authorities-claim-name} — but the claim is
 * the source and the authorities are a projection of it for {@code hasRole()}. Deriving the domain
 * type from the projection would mean stripping a prefix this class did not add.
 */
@Service
class JwtIdentityApi implements IdentityApi {

    private static final String ROLES_CLAIM = "roles";
    private static final String EMAIL_CLAIM = "email";

    @Override
    public Optional<CurrentAccount> currentAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        return authentication instanceof JwtAuthenticationToken token
                ? Optional.of(accountOf(token.getToken()))
                : Optional.empty();
    }

    @Override
    public CurrentAccount requireCurrentAccount() {
        return currentAccount()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "No authenticated account on a route that requires one"));
    }

    private static CurrentAccount accountOf(Jwt token) {
        return new CurrentAccount(accountIdOf(token), token.getClaimAsString(EMAIL_CLAIM), rolesOf(token));
    }

    /**
     * {@code sub} is the UUID of the account, never the email ({@code docs/08-identidad-y-seguridad.md}
     * §4). Anything else means this service and ukun-auth disagree about the token, and the honest
     * answer to a token we cannot read is to reject it rather than to invent an identity for it.
     */
    private static UUID accountIdOf(Jwt token) {
        try {
            return UUID.fromString(token.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("The subject claim is not an account identifier", e);
        }
    }

    /**
     * An unknown name is dropped rather than rejected. ukun-auth can grow a role before this service
     * knows what it means, and failing every request of everyone who holds it would turn a routine
     * deployment into an outage — whereas ignoring it only means that role opens nothing here, which
     * is already true.
     */
    private static Set<Role> rolesOf(Jwt token) {
        List<String> claimed = token.getClaimAsStringList(ROLES_CLAIM);
        if (claimed == null) {
            return Set.of();
        }

        return claimed.stream()
                .map(JwtIdentityApi::roleOrNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Role roleOrNull(String name) {
        try {
            return Role.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
