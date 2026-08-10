package dev.sandoretti.ukun.api.identity.infra;

import dev.sandoretti.ukun.api.identity.application.AuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * The HTTP client {@link HttpAccountsAdapter} speaks to ukun-auth with.
 *
 * <p>The interceptor is what makes this a service-to-service call rather than a forwarded user
 * request: it obtains a {@code client_credentials} token for the {@code ukun-auth} registration of
 * {@code application.yaml} and attaches it, renewing it when it expires. Relaying the caller's token
 * instead would be wrong twice over — somebody signing up has no token yet, and no user token carries
 * the {@code accounts:write} scope.
 */
@Configuration
class AccountsClientConfig {

    /**
     * {@code AuthorizedClientServiceOAuth2AuthorizedClientManager}, not the {@code Default} one Boot
     * would otherwise supply. The default is built for a browser flow and resolves the authorized
     * client from the current {@code HttpServletRequest}; the signup saga's compensation and its
     * retries can run outside any request, and there the default fails with a message about a missing
     * request rather than about the token it could not get.
     */
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository registrations) {
        OAuth2AuthorizedClientService clients = new InMemoryOAuth2AuthorizedClientService(registrations);

        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());

        return manager;
    }

    @Bean
    RestClient accountsRestClient(
            RestClient.Builder builder, OAuth2AuthorizedClientManager authorizedClientManager, AuthProperties properties) {

        var interceptor = new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
        interceptor.setClientRegistrationIdResolver(request -> HttpAccountsAdapter.REGISTRATION_ID);

        return builder.baseUrl(properties.getBaseUrl())
                .requestInterceptor(interceptor)
                .build();
    }
}
