package dev.sandoretti.ukun.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;

/**
 * The routing table itself, read the way Spring reads it, without starting a server. It is the
 * versioned replacement for {@code nginx/nginx.conf} (skill {@code spring-gateway} §4), and a table
 * is exactly the kind of thing that should be assertable as data.
 *
 * <p>Two of these tests are about what is <strong>not</strong> there. No route reaches
 * {@code /internal/**}, which is how those endpoints stay unreachable from a browser
 * ({@code docs/08-identidad-y-seguridad.md} §3); and every prefix that <em>is</em> routed is also
 * reserved from the SPA fallback, so adding a sixth route without touching {@link ReservedPaths}
 * fails here instead of silently turning that prefix into an HTML page once the bundle is served.
 */
class RouteDefinitionsTest {

    private static final URI API = URI.create("http://ukun-api:8081");
    private static final URI AUTH = URI.create("http://ukun-auth:9000");

    private final List<RouteDefinition> routes = declaredRoutes();

    @Test
    @DisplayName("routes every prefix of the contract to the service that owns it")
    void routesEveryPrefixOfTheContract() {
        assertThat(routes)
                .extracting(RouteDefinition::getId, RouteDefinition::getUri, RouteDefinitionsTest::pathOf)
                .containsExactlyInAnyOrder(
                        tuple("api", API, "/api/**"),
                        tuple("media", API, "/media/**"),
                        tuple("auth-oauth2", AUTH, "/oauth2/**"),
                        tuple("auth-login", AUTH, "/login/oauth2/**"),
                        tuple("auth-well-known", AUTH, "/.well-known/**"));
    }

    @Test
    @DisplayName("routes nothing to the internal endpoints of ukun-auth")
    void routesNothingToTheInternalEndpoints() {
        assertThat(routes).noneSatisfy(route -> assertThat(pathOf(route)).startsWith("/internal"));
    }

    @Test
    @DisplayName("keeps every routed prefix out of the SPA fallback")
    void keepsEveryRoutedPrefixOutOfTheSpaFallback() {
        assertThat(routes).allSatisfy(route ->
                assertThat(ReservedPaths.covers(aPathUnder(pathOf(route)))).isTrue());
    }

    private static String pathOf(RouteDefinition route) {
        return route.getPredicates().stream()
                .filter(predicate -> "Path".equals(predicate.getName()))
                .map(PredicateDefinition::getArgs)
                .flatMap(args -> args.values().stream())
                .findFirst()
                .orElseThrow(() -> new AssertionError("route %s declares no Path predicate".formatted(route.getId())));
    }

    /** {@code /api/**} to {@code /api/anything}, so the pattern can be tested as a real request. */
    private static String aPathUnder(String pattern) {
        return pattern.replace("**", "anything");
    }

    /**
     * The service's own {@code application.yaml}, bound to Spring Cloud Gateway's properties with
     * placeholders resolved — so {@code ${ukun.gateway.api-uri}} and the environment defaults behind
     * it are exercised too, and a route pointing at an undefined property fails here.
     */
    private static List<RouteDefinition> declaredRoutes() {
        MutablePropertySources sources = new MutablePropertySources();
        try {
            new YamlPropertySourceLoader()
                    .load("application", new ClassPathResource("application.yaml"))
                    .forEach(sources::addLast);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the service's application.yaml", e);
        }

        Binder binder = new Binder(
                ConfigurationPropertySources.from(sources), new PropertySourcesPlaceholdersResolver(sources));

        return binder.bind("spring.cloud.gateway.server.webflux", GatewayProperties.class)
                .orElseThrow(() -> new AssertionError("no gateway routes are declared"))
                .getRoutes();
    }
}
