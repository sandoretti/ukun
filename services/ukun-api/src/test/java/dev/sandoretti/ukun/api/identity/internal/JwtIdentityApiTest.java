package dev.sandoretti.ukun.api.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sandoretti.ukun.api.identity.CurrentAccount;
import dev.sandoretti.ukun.api.identity.Role;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * How a validated token becomes a {@link CurrentAccount} — the value {@code /api/me/profile} and the
 * signup saga will resolve every owned resource from.
 *
 * <p>The token here is built rather than signed: whether a signature, an issuer and an audience are
 * checked is {@code ResourceServerIT}'s subject, and by the time this class runs that has already
 * happened. What is left is the reading, and it has one rule worth a test of its own — the subject is
 * an account id, never an email.
 */
class JwtIdentityApiTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("8f2c1e40-0000-4000-8000-000000000001");

    private final JwtIdentityApi identity = new JwtIdentityApi();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("the account comes from the subject claim, not from the email")
    void shouldReadTheAccountFromTheSubject() {
        authenticate(ACCOUNT_ID.toString(), Map.of("email", "customer@ukun.dev", "roles", List.of("CUSTOMER")));

        assertThat(identity.requireCurrentAccount())
                .isEqualTo(new CurrentAccount(ACCOUNT_ID, "customer@ukun.dev", Set.of(Role.CUSTOMER)));
    }

    @Test
    @DisplayName("an employee who is also an admin keeps both roles")
    void shouldKeepEveryKnownRole() {
        authenticate(ACCOUNT_ID.toString(), Map.of("email", "admin@ukun.dev", "roles", List.of("EMPLOYEE", "ADMIN")));

        assertThat(identity.requireCurrentAccount().roles()).containsExactlyInAnyOrder(Role.EMPLOYEE, Role.ADMIN);
    }

    /**
     * ukun-auth can grow a role before this service knows what it means. Rejecting the request would
     * turn a routine deployment of the other service into an outage here; dropping the name only means
     * that role opens nothing, which is already true.
     */
    @Test
    @DisplayName("a role this service does not know is ignored, not fatal")
    void shouldIgnoreAnUnknownRole() {
        authenticate(ACCOUNT_ID.toString(), Map.of("roles", List.of("CUSTOMER", "AUDITOR")));

        assertThat(identity.requireCurrentAccount().roles()).containsExactly(Role.CUSTOMER);
    }

    /**
     * The legacy keyed identity by email. If a token ever arrived shaped that way, silently accepting it
     * would make every owned resource follow whoever holds that address next.
     */
    @Test
    @DisplayName("a subject that is not an account id is rejected rather than interpreted")
    void shouldRejectANonUuidSubject() {
        authenticate("customer@ukun.dev", Map.of("roles", List.of("CUSTOMER")));

        assertThatThrownBy(identity::requireCurrentAccount).isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    @DisplayName("no authentication is an empty answer, not a null one")
    void shouldAnswerEmptyWhenNobodyIsAuthenticated() {
        assertThat(identity.currentAccount()).isEmpty();
    }

    @Test
    @DisplayName("requiring an account on an unprotected route is a configuration bug, and says so")
    void shouldFailLoudlyWhenAnAccountIsRequiredAndAbsent() {
        assertThatThrownBy(identity::requireCurrentAccount)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private static void authenticate(String subject, Map<String, Object> claims) {
        var builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(builder::claim);

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build()));
    }
}
