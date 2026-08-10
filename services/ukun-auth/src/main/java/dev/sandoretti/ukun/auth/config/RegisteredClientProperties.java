package dev.sandoretti.ukun.auth.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * The two OAuth2 clients of this system, as configuration rather than as constants — because their
 * secrets differ per environment and a secret has no business being in a file that is committed.
 *
 * <p>They are registered here and not left to whoever needs them first, so that v0.5 (the gateway's
 * {@code oauth2Login}) and issue #25 (the signup saga's calls to {@code /internal/accounts}) find
 * their client already in place and do not have to touch this service again.
 *
 * <p>Deliberately not Boot's own {@code spring.security.oauth2.authorizationserver.client.*}
 * properties: those build an in-memory {@code RegisteredClientRepository}, and this service stores
 * its clients in {@code oauth2_registered_client} — the table is in the baseline, and a client that
 * only exists in memory disappears with every restart along with any consent granted against it.
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "ukun.oauth2")
public class RegisteredClientProperties {

    /** The BFF: authorization code with PKCE, on behalf of a person (ADR-0003). */
    private Client gateway = new Client();

    /** {@code ukun-api} calling {@code /internal/accounts}: client credentials, no person involved. */
    private Client api = new Client();

    @Getter
    @Setter
    public static class Client {

        private String clientId;

        private String secret;

        private List<String> redirectUris = List.of();

        private List<String> postLogoutRedirectUris = List.of();

        /**
         * Hand-written for the same reason as {@code AccountCredentials}: {@code docs/11} §4 forbids
         * a secret in a log line, and Boot prints bound configuration properties at {@code DEBUG}.
         * The ArchUnit rule that catches this shape only looks at {@code *Request}/{@code *Command}
         * classes, so nothing would have failed the build.
         */
        @Override
        public String toString() {
            return "Client[clientId=%s, redirectUris=%s, secret=***]".formatted(clientId, redirectUris);
        }
    }
}
