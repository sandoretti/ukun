package dev.sandoretti.ukun.api.identity.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Where ukun-auth answers for service-to-service calls. Same shape as {@code StorageProperties} for
 * the {@code storage} module: the port's adapter reads its one piece of configuration from here
 * instead of carrying it hardcoded, which is defect #6 in the legacy.
 *
 * <p>The default is the container name, not the issuer of the token. They are different values on
 * purpose ({@code application.yaml} explains the pair): {@code /internal/accounts} is reached across
 * the Docker network and no browser takes part, whereas {@code iss} is what a browser sees.
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "ukun.auth")
public class AuthProperties {

    private String baseUrl = "http://ukun-auth:9000";
}
