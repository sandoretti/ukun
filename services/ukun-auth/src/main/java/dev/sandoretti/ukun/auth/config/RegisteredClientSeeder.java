package dev.sandoretti.ukun.auth.config;

import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

/**
 * Registers the two clients of this system if they are not already there, on every start.
 *
 * <p>It is a runner and not a Liquibase changeset on purpose. The rows carry a hashed client secret
 * that differs per environment, and a changeset that inserted one would either hardcode a secret in
 * a versioned file or freeze an environment-specific value into a checksum that must never change
 * (skill {@code liquibase}, rule 1). The schema is Liquibase's; this row is configuration.
 *
 * <p>Idempotent by {@code client_id}, which is what {@code ux_oauth2_registered_client_id} in the
 * baseline turns from an assumption into a guarantee: an existing client keeps its primary key and
 * is updated in place, so restarting does not orphan the authorizations already granted against it.
 */
@Component
class RegisteredClientSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RegisteredClientSeeder.class);

    /** Scopes of the customer-facing token. {@code ukun-api} maps them to read and write access. */
    private static final String READ_SCOPE = "ukun.read";

    private static final String WRITE_SCOPE = "ukun.write";

    /** The only scope of the service-to-service token: the signup saga writing accounts (docs/08 §3). */
    private static final String ACCOUNTS_WRITE_SCOPE = "accounts:write";

    private final RegisteredClientRepository clients;
    private final PasswordEncoder passwordEncoder;
    private final RegisteredClientProperties properties;

    RegisteredClientSeeder(
            RegisteredClientRepository clients,
            PasswordEncoder passwordEncoder,
            RegisteredClientProperties properties) {
        this.clients = clients;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        register(gateway(properties.getGateway()));
        register(api(properties.getApi()));
    }

    private void register(RegisteredClient client) {
        RegisteredClient existing = clients.findByClientId(client.getClientId());

        // Reusing the existing primary key is what makes this an update rather than a second row.
        RegisteredClient toSave = existing == null
                ? client
                : RegisteredClient.from(client).id(existing.getId()).build();

        clients.save(toSave);

        // The client id is not a secret; the secret is, and it is nowhere in this line (docs/11 §4).
        log.info(
                "OAuth2 client {} {}", client.getClientId(), existing == null ? "registered" : "already present");
    }

    /**
     * The BFF. Authorization code with <strong>PKCE required</strong> and refresh token rotation: the
     * SPA never sees a token, only an {@code HttpOnly} session cookie against the gateway (ADR-0003,
     * docs/08 §4).
     *
     * <p>{@code requireAuthorizationConsent(false)} because this is a first-party client — the
     * consent screen exists to protect a user from a third party, and there is none here.
     */
    private RegisteredClient gateway(RegisteredClientProperties.Client configured) {
        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(configured.getClientId())
                .clientSecret(passwordEncoder.encode(configured.getSecret()))
                .clientName("Ukun gateway (BFF)")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(READ_SCOPE)
                .scope(WRITE_SCOPE)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(Duration.ofMinutes(15))
                        .refreshTokenTimeToLive(Duration.ofDays(7))
                        .reuseRefreshTokens(false)
                        .build());

        configured.getRedirectUris().forEach(builder::redirectUri);
        configured.getPostLogoutRedirectUris().forEach(builder::postLogoutRedirectUri);

        return builder.build();
    }

    /**
     * {@code ukun-api} calling {@code /internal/accounts} during the signup saga. Client credentials,
     * one scope, no redirect URI and no refresh token — there is no browser and no person in this
     * flow, and it is never routed through the gateway (docs/08 §3).
     */
    private RegisteredClient api(RegisteredClientProperties.Client configured) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(configured.getClientId())
                .clientSecret(passwordEncoder.encode(configured.getSecret()))
                .clientName("Ukun API (service to service)")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(ACCOUNTS_WRITE_SCOPE)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .build())
                .build();
    }
}
