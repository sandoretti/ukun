package dev.sandoretti.ukun.gateway.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
 * <em>which</em> backend received a path rather than only that something did. It also records the
 * paths it was asked for, which is how {@code GatewayRoutingIT} proves the other half of the
 * {@code /internal/**} rule: not merely that the client got a 404, but that nothing was forwarded.
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

    private final String name;
    private final HttpServer server;
    private final List<String> requestedPaths = new CopyOnWriteArrayList<>();

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
    }

    public static void forgetRequests() {
        API.requestedPaths.clear();
        AUTH.requestedPaths.clear();
        WEB.requestedPaths.clear();
    }

    public String uri() {
        return "http://localhost:%d".formatted(server.getAddress().getPort());
    }

    /** What this backend was actually asked for, in order. */
    public List<String> requestedPaths() {
        return List.copyOf(requestedPaths);
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
                stub.requestedPaths.add(path);

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
}
