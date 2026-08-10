package dev.sandoretti.ukun.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOAuth2Login;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import dev.sandoretti.ukun.gateway.support.UpstreamStub;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Logging out, which in a BFF is two logouts: the session here, and the one {@code ukun-auth} keeps.
 * Ending only the first is the bug this test exists to prevent — the next click on "log in" would
 * walk straight back through without a password, because the authorization server would still
 * recognise the browser and issue a code without prompting.
 */
@SpringBootTest
class LogoutIT {

    @DynamicPropertySource
    static void upstreams(DynamicPropertyRegistry registry) {
        UpstreamStub.bind(registry);
    }

    @Autowired
    private ApplicationContext context;

    /** Injected rather than rebuilt, so the flow under test uses the registration the service runs with. */
    @Autowired
    private ReactiveClientRegistrationRepository clients;

    private WebTestClient http;

    @BeforeEach
    void bindToTheFilterChain() {
        http = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("sends the browser on to end the session at ukun-auth")
    void sendsTheBrowserOnToEndTheSessionAtAuth() {
        logout().expectStatus()
                .is3xxRedirection()
                .expectHeader()
                // The end session endpoint of the provider metadata ClientRegistrationConfig fills in
                // by hand — there is no discovery document in this system to have read it from.
                .value("Location", location -> {
                    assertThat(location)
                            .startsWith(UpstreamStub.BROWSER_FACING_ISSUER + "/connect/logout")
                            .contains("id_token_hint=id-token")
                            // Sent as the registered literal, trailing slash and all: ukun-auth
                            // compares it as a string, and {baseUrl} would expand without it.
                            .contains("post_logout_redirect_uri=" + UpstreamStub.POST_LOGOUT_REDIRECT_URI);
                });
    }

    /**
     * {@code POST} and nothing else. A logout reachable by {@code GET} is a logout any page can
     * trigger with an image tag, which is the same class of defect as the mutating {@code GET}s of
     * the legacy ({@code docs/03-analisis-y-patrones.md} §1) — and the reason {@code /logout} is a
     * reserved path rather than a route the SPA can own.
     */
    @Test
    @DisplayName("ignores a logout attempted with GET")
    void ignoresALogoutAttemptedWithGet() {
        http.mutateWith(mockOAuth2Login().oauth2User(person()).clientRegistration(registration()))
                .get()
                .uri("/logout")
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    private WebTestClient.ResponseSpec logout() {
        return http.mutateWith(mockOAuth2Login().oauth2User(person()).clientRegistration(registration()))
                .mutateWith(csrf())
                .post()
                .uri("/logout")
                .exchange();
    }

    private ClientRegistration registration() {
        return clients.findByRegistrationId("ukun").block();
    }

    /**
     * An {@code OidcUser} and not a bare {@code OAuth2User}: the handler reads the id token from the
     * principal to send {@code id_token_hint}, and without it the redirect would silently degrade to
     * one the authorization server cannot attribute to a session.
     */
    private static DefaultOidcUser person() {
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token")
                .issuer(UpstreamStub.BROWSER_FACING_ISSUER)
                .subject("customer@ukun.dev")
                .build();

        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")), idToken, "sub");
    }
}
