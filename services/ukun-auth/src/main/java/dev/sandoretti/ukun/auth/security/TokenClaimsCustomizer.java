package dev.sandoretti.ukun.auth.security;

import static java.util.stream.Collectors.toCollection;

import dev.sandoretti.ukun.auth.application.AccountCredentials;
import dev.sandoretti.ukun.auth.application.AccountsService;
import dev.sandoretti.ukun.auth.domain.Role;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

/**
 * The access token of {@code docs/08-identidad-y-seguridad.md} §4, which is not what the
 * Authorization Server emits by default.
 *
 * <p>Three differences, each of which would otherwise surface as a bug somewhere else:
 *
 * <ul>
 *   <li><strong>{@code aud}</strong>. By default it is the {@code client_id} — that is, the gateway —
 *       and {@code ukun-api} validating {@code aud=ukun-api} (issue #24) would reject every token.
 *   <li><strong>{@code sub}</strong>. By default it is the principal's name, which here is the email,
 *       because that is what a person types into the login form. The contract says {@code sub} is the
 *       account's UUID, and it has to be: {@code ukun-api} resolves the customer by
 *       {@code account_id}, and a changed email would otherwise silently change the identity of every
 *       resource.
 *   <li><strong>{@code email} and {@code roles}</strong>, which no standard grant puts there.
 * </ul>
 *
 * <p><strong>And one claim that is deliberately absent: {@code storeId}.</strong> It would save a
 * lookup per stock operation, at the price of teaching this service what a store is, of freezing a
 * business authorisation decision into a credential, and of an employee transfer not taking effect
 * until their token expired. {@code ukun-api} resolves the store from {@code account_id} through a
 * unique index (docs/08 §4).
 */
@Component
class TokenClaimsCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    /**
     * The single audience of a user token. There is one resource server in this system and naming it
     * here is what lets {@code ukun-api} reject a token minted for anybody else.
     *
     * <p>It travels in a mutable {@code ArrayList} rather than the obvious {@code List.of(…)}, for the
     * reason spelled out on {@link #rolesOf}: an immutable list of the JDK cannot be read back out of
     * {@code oauth2_authorization}, and the breakage surfaces in the logout.
     */
    private static final String RESOURCE_SERVER = "ukun-api";

    private final AccountsService accounts;

    TokenClaimsCustomizer(AccountsService accounts) {
        this.accounts = accounts;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return;
        }

        // A client credentials token has no person behind it: it is ukun-api calling
        // /internal/accounts, so its audience is this service and it carries neither email nor roles.
        // Leaving the default alone is the correct answer, not an omission.
        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
            return;
        }

        context.getClaims().audience(new ArrayList<>(List.of(RESOURCE_SERVER)));

        accounts.findCredentialsByEmail(context.getPrincipal().getName()).ifPresent(account -> context.getClaims()
                .subject(account.accountId().toString())
                .claim("email", account.email())
                .claim("roles", rolesOf(account)));
    }

    /**
     * Names, not enum instances: a claim has to survive JSON serialisation unambiguously.
     *
     * <p><strong>And an {@code ArrayList}, not the {@code List.of(…)} or {@code Stream.toList()} it
     * would naturally be</strong> — same reason as the audience above. Every claim set here is stored
     * with the {@code OAuth2Authorization} in {@code oauth2_authorization}, serialised by the
     * {@code ObjectMapper} of {@link org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService},
     * which writes each value's concrete type and reads it back through a {@code PolymorphicTypeValidator}
     * with a fixed allow-list. The JDK's immutable lists —
     * {@code java.util.ImmutableCollections$List12}/{@code $ListN} — are not on it, so the write
     * succeeds and the <em>read</em> fails.
     *
     * <p>The failure lands nowhere near here, which is why this comment is long: the first read of that
     * row is the {@code id_token_hint} of the RP-initiated logout, so the symptom was
     * {@code /connect/logout} answering 400, the session at this server never ending, and «sign in»
     * silently returning the previous user (issue #106).
     */
    private static List<String> rolesOf(AccountCredentials account) {
        return account.roles().stream().map(Role::name).collect(toCollection(ArrayList::new));
    }
}
