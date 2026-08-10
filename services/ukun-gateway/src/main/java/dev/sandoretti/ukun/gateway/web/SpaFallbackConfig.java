package dev.sandoretti.ukun.gateway.web;

import dev.sandoretti.ukun.gateway.config.ReservedPaths;
import dev.sandoretti.ukun.gateway.config.UkunGatewayProperties;
import java.io.IOException;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.function.server.support.RouterFunctionMapping;
import reactor.core.publisher.Mono;

/**
 * Serves the built SPA and its history fallback: an unknown path returns {@code index.html}, because
 * the router lives in the browser (skill {@code spring-gateway} §6).
 *
 * <h2>The ordering trap this class exists to avoid</h2>
 *
 * A {@code RouterFunction} declared as a plain bean would be picked up by the autoconfigured
 * {@code RouterFunctionMapping}, whose order is <strong>-1</strong> — ahead of the gateway's
 * {@code RoutePredicateHandlerMapping} at <strong>1</strong>. The SPA fallback would then swallow
 * {@code /api/**} and turn every backend call into an HTML page. So the router is built here and
 * handed to a mapping of our own, ordered last among the ones that matter — see
 * {@link #BEFORE_THE_STATIC_RESOURCE_HANDLER}. The autoconfigured one finds no
 * {@code RouterFunction} bean and stays inert.
 *
 * <p>Order alone is not the whole defence: the fallback predicate also refuses the reserved prefixes
 * outright ({@link ReservedPaths}), which is what makes {@code /internal/**} answer 404 instead of a
 * friendly HTML page. Two independent reasons for the same guarantee, because this is the kind of
 * thing that breaks silently.
 *
 * <h2>Nothing packages the bundle here yet</h2>
 *
 * {@code ukun.gateway.web-root} points at where the image will mount the Vite build; producing it
 * needs a build stage over {@code apps/ukun-web} and belongs to the deployment slice. Until then the
 * directory is absent and the fallback answers 404 — which is exactly what an empty directory does,
 * so nothing here has to know the difference. Under {@code tilt up} this mapping never runs at all:
 * {@code DevSpaRouteConfig} routes the same paths to Vite from a mapping that comes first.
 */
@Configuration(proxyBeanMethods = false)
class SpaFallbackConfig {

    private static final String INDEX = "index.html";

    /**
     * Last of everything that matters, but ahead of Boot's static resource handler, which sits at
     * {@code LOWEST_PRECEDENCE - 1} and is mapped to {@code /**}. That one does not decline what it
     * cannot find: it answers 404 itself, so a fallback placed behind it is never consulted. Being
     * in front of it is also correct, not merely convenient — the SPA's own assets live under
     * {@code web-root}, not under {@code classpath:/static/}.
     */
    private static final int BEFORE_THE_STATIC_RESOURCE_HANDLER = Ordered.LOWEST_PRECEDENCE - 2;

    @Bean
    RouterFunctionMapping spaFallbackMapping(UkunGatewayProperties properties, ResourceLoader resourceLoader) {
        Resource webRoot = resourceLoader.getResource(properties.webRoot());

        RouterFunction<ServerResponse> spa = RouterFunctions.resources("/**", webRoot)
                .andRoute(spaFallback(), request -> index(webRoot));

        RouterFunctionMapping mapping = new RouterFunctionMapping(spa);
        mapping.setOrder(BEFORE_THE_STATIC_RESOURCE_HANDLER);

        return mapping;
    }

    /**
     * What counts as "a route of the client-side router". Three conditions beyond the obvious one:
     *
     * <ul>
     *   <li><strong>Outside the reserved prefixes.</strong> The load-bearing one — see
     *       {@link ReservedPaths}.
     *   <li><strong>{@code text/html} acceptable.</strong> A {@code fetch} asking for JSON on a path
     *       that does not exist should get a 404, not a page.
     *   <li><strong>No extension on the last segment.</strong> {@code Accept} alone does not settle
     *       it: a browser requests scripts with {@code Accept: &#42;/&#42;}, which is compatible
     *       with {@code text/html}, so without this a missing {@code /assets/app-abc123.js} answers
     *       200 with the HTML shell and the failure would surface as a syntax error in the console
     *       instead of as the 404 it is.
     * </ul>
     */
    static RequestPredicate spaFallback() {
        return RequestPredicates.GET("/**")
                .or(RequestPredicates.HEAD("/**"))
                .and(RequestPredicates.accept(MediaType.TEXT_HTML))
                .and(SpaFallbackConfig::isClientSideRoute);
    }

    private static boolean isClientSideRoute(ServerRequest request) {
        String path = request.requestPath().value();

        return !ReservedPaths.covers(path) && !hasExtension(path);
    }

    private static boolean hasExtension(String path) {
        int lastSegment = path.lastIndexOf('/');
        return path.indexOf('.', lastSegment + 1) >= 0;
    }

    /**
     * {@code exists()} is checked on every request rather than once at startup, so that a build
     * appearing under the mounted directory starts being served without a restart.
     */
    private static Mono<ServerResponse> index(Resource webRoot) {
        return indexOf(webRoot)
                .filter(Resource::exists)
                .map(index -> ServerResponse.ok().contentType(MediaType.TEXT_HTML).bodyValue(index))
                .orElseGet(() -> ServerResponse.notFound().build());
    }

    private static Optional<Resource> indexOf(Resource webRoot) {
        try {
            return Optional.of(webRoot.createRelative(INDEX));
        } catch (IOException malformedLocation) {
            // A web-root that cannot even be resolved into a child is a misconfigured deployment,
            // not a request-time failure: answering 404 keeps it out of the response and leaves the
            // symptom where it belongs, in the configuration.
            return Optional.empty();
        }
    }
}
