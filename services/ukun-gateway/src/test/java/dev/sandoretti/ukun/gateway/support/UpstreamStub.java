package dev.sandoretti.ukun.gateway.support;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Stands in for {@code ukun-api} and {@code ukun-auth} at the HTTP boundary, which is the only place
 * this service touches them: it routes, it does not know what they do.
 *
 * <p>The JDK's own HTTP server, with no new dependency — the same choice, and for the same reason,
 * as {@code JwksTestServer} in {@code ukun-api}: what has to be exercised here is the gateway's
 * routing table, and standing up two real services would only tie these tests to their startup and
 * their databases.
 *
 * <p>Every request is echoed back with the name of the stub that answered it, so a test can assert
 * <em>which</em> backend received a path rather than only that something did. It also records what it
 * was asked for — path and headers — which is how two rules get proved from the receiving end rather
 * than from the response: {@code GatewayRoutingIT} shows that an {@code /internal/**} request is not
 * merely refused but never forwarded, and {@code TokenRelayIT} shows that the {@code Authorization}
 * header reaches {@code ukun-api} and does not reach {@code ukun-auth}.
 *
 * <p>Both stubs are started once for the whole JVM and shared between test classes, so that every
 * {@code @SpringBootTest} binds the same properties and Spring reuses one application context. They
 * are stopped by a shutdown hook rather than an {@code @AfterAll}, which no single class could own.
 */
public final class UpstreamStub {

    public static final UpstreamStub API = start("api");
    public static final UpstreamStub AUTH = start("auth");

    /** Vite's dev server. Only the {@code dev} profile routes to it — see {@code DevSpaRoutingIT}. */
    public static final UpstreamStub WEB = start("web");

    /** What {@code ukun-auth} looks like from a browser, which is not how this process reaches it. */
    public static final String BROWSER_FACING_ISSUER = "http://localhost:9000";

    /** With the trailing slash {@code ukun-auth} registered, which the logout assertion depends on. */
    public static final String POST_LOGOUT_REDIRECT_URI = "http://localhost:8080/";

    private final String name;
    private final HttpServer server;
    private final List<Received> received = new CopyOnWriteArrayList<>();

    /**
     * One request as this backend saw it. Headers are flattened to their first value and keyed in
     * lower case, because {@code com.sun.net.httpserver} normalises them on the way in and a test
     * should not have to guess which capitalisation survived.
     */
    public record Received(String path, Map<String, String> headers) {

        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    private UpstreamStub(String name, HttpServer server) {
        this.name = name;
        this.server = server;
    }

    /**
     * Points the gateway's two destinations at these stubs, and the SPA root at a bundle of one
     * file. It is a single method so that every fixture binds exactly the same set: a test class
     * binding one property differently would silently get an application context of its own.
     */
    public static void bind(DynamicPropertyRegistry registry) {
        registry.add("ukun.gateway.api-uri", API::uri);
        registry.add("ukun.gateway.auth-uri", AUTH::uri);
        registry.add("ukun.gateway.web-uri", WEB::uri);
        registry.add("ukun.gateway.web-root", () -> "classpath:spa/");

        registry.add("ukun.oauth2.client-id", () -> "ukun-gateway");
        // The property has no default in application.yaml, on purpose — so it has to be given here
        // or no context starts at all.
        registry.add("ukun.oauth2.secret", () -> "test-secret");

        // Browser-facing, and deliberately NOT the stub: these are the URLs a browser is sent to, and
        // keeping them pointed somewhere unreachable from this process is what lets OAuth2LoginIT
        // assert the split — the redirect names this host, and nothing was proxied to reach it.
        registry.add("ukun.oauth2.issuer", () -> BROWSER_FACING_ISSUER);
        registry.add("ukun.oauth2.authorization-uri", () -> BROWSER_FACING_ISSUER + "/oauth2/authorize");
        registry.add("ukun.oauth2.end-session-uri", () -> BROWSER_FACING_ISSUER + "/connect/logout");
        registry.add("ukun.oauth2.post-logout-redirect-uri", () -> POST_LOGOUT_REDIRECT_URI);

        // Container-facing: fetched by the gateway itself, so these do point at the stub.
        registry.add("ukun.oauth2.token-uri", () -> AUTH.uri() + "/oauth2/token");
        // Not the AUTH stub: the gateway FETCHES this one, and it has to answer a real JWK set rather
        // than the echo. Publishing it at the stub's own /oauth2/jwks would take that path away from
        // the tests that assert the browser-facing half — that the same path, asked through the
        // gateway, is proxied to ukun-auth (TokenRelayIT, TracePropagationIT).
        registry.add("ukun.oauth2.jwk-set-uri", AccessTokens::jwkSetUri);
    }

    public static void forgetRequests() {
        API.received.clear();
        AUTH.received.clear();
        WEB.received.clear();
    }

    public String uri() {
        return "http://localhost:%d".formatted(server.getAddress().getPort());
    }

    /** What this backend was actually asked for, in order. */
    public List<String> requestedPaths() {
        return received.stream().map(Received::path).toList();
    }

    /** The same requests with their headers, for the assertions that are about what was forwarded. */
    public List<Received> received() {
        return List.copyOf(received);
    }

    /** The body this stub answers with, for a test that wants to assert the response came from here. */
    public String echoOf(String path) {
        return body(name, path);
    }

    private static UpstreamStub start(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            UpstreamStub stub = new UpstreamStub(name, server);

            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                stub.received.add(new Received(path, firstValueOf(exchange.getRequestHeaders())));

                // Draining the request body matters: a client that is still writing when the
                // response arrives sees a connection reset instead of the status under test.
                try (InputStream request = exchange.getRequestBody()) {
                    request.readAllBytes();
                }
                byte[] response = body(name, path).getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (var body = exchange.getResponseBody()) {
                    body.write(response);
                }
            });
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));

            return stub;

        } catch (IOException e) {
            throw new IllegalStateException("Could not start the %s upstream stub".formatted(name), e);
        }
    }

    private static String body(String name, String path) {
        return "{\"upstream\":\"%s\",\"path\":\"%s\"}".formatted(name, path);
    }

    private static Map<String, String> firstValueOf(Headers headers) {
        Map<String, String> flattened = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            if (!values.isEmpty()) {
                flattened.put(name.toLowerCase(Locale.ROOT), values.getFirst());
            }
        });

        return Map.copyOf(flattened);
    }
}
