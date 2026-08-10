package dev.sandoretti.ukun.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import dev.sandoretti.ukun.gateway.support.UpstreamStub;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The half of issue #30 that only this service can prove: that the trace <em>leaves</em> the gateway.
 * A {@code traceId} in three separate logs is worth nothing unless it is the same one, and what makes
 * it the same one is the {@code traceparent} header this test reads off the receiving end.
 *
 * <p>Asserted from {@link UpstreamStub}'s recording rather than from the response, for the reason
 * {@code TokenRelayIT} gives about the {@code Authorization} header: whether the gateway attached
 * anything on the way out is invisible to the client that made the request.
 *
 * <p>There is no code in this service that does any of this. The header is emitted by the
 * instrumentation Micrometer Tracing installs once it is on the classpath, and this test exists
 * because that is exactly the kind of behaviour that disappears silently — a dependency dropped, a
 * property renamed, {@code management.tracing.export.enabled} turned off — with nothing failing to
 * compile.
 *
 * <h2>Why the span is asserted and not only the trace</h2>
 *
 * A gateway forwards the headers it received. So a test that only checked "the trace id arrives at
 * {@code ukun-api}" passes on a service with <em>no tracing at all</em>, purely because the proxy
 * copied the header across. That is not a hypothetical: it is what this suite reported while
 * {@code micrometer-tracing-bridge-otel} was missing from the classpath, with the second test below
 * as the only red one. The span changing on the hop is what separates instrumentation from a copy.
 */
class TracePropagationIT extends GatewayIntegrationTest {

    private static final String TRACEPARENT = "traceparent";

    /** {@code version-traceid-spanid-flags}, the W3C shape. 32 hex for the trace, 16 for the span. */
    private static final String W3C = "^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$";

    @Test
    @DisplayName("forwards the trace it was given to ukun-api, under a span of its own")
    void forwardsTheIncomingTraceToTheApi() {
        String traceId = aTraceId();
        String spanId = aSpanId();

        http.get()
                .uri("/api/products")
                .header(TRACEPARENT, traceparentOf(traceId, spanId))
                .exchange()
                .expectStatus()
                .isOk();

        assertSoftly(softly -> {
            String forwarded = UpstreamStub.API.received().getFirst().header(TRACEPARENT);
            softly.assertThat(forwarded).as("ukun-api received no traceparent").isNotNull();
            softly.assertThat(forwarded).matches(W3C);
            // The trace does not change: it is the whole contract between the three services.
            softly.assertThat(forwarded).contains(traceId);
            // The span does, because this hop is a new one. See the note on the class.
            softly.assertThat(forwarded).doesNotContain(spanId);
        });
    }

    /**
     * The case that actually happens. No browser sends {@code traceparent}, so if the gateway only
     * relayed traces it was given, every real request would arrive at {@code ukun-api} untraced and
     * this release would have bought nothing.
     */
    @Test
    @DisplayName("mints a trace for a request that arrives without one")
    void mintsATraceForABrowserRequest() {
        http.get().uri("/api/products").exchange().expectStatus().isOk();

        assertThat(UpstreamStub.API.received())
                .singleElement()
                .extracting(received -> received.header(TRACEPARENT))
                .asString()
                .matches(W3C);
    }

    /**
     * {@code ukun-auth} is reached through this service too, and the browser's leg of the login flow
     * is the one the saga of issue #25 is debugged from. A trace that stopped at the {@code api} route
     * would leave exactly that flow uncorrelated.
     */
    @Test
    @DisplayName("also traces what it forwards to ukun-auth")
    void forwardsTheTraceToAuthAsWell() {
        String traceId = aTraceId();

        http.get()
                .uri("/oauth2/jwks")
                .header(TRACEPARENT, traceparentOf(traceId, aSpanId()))
                .exchange()
                .expectStatus()
                .isOk();

        assertThat(UpstreamStub.AUTH.received())
                .singleElement()
                .extracting(received -> received.header(TRACEPARENT))
                .asString()
                .contains(traceId);
    }

    private static String aTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String aSpanId() {
        return aTraceId().substring(0, 16);
    }

    private static String traceparentOf(String traceId, String spanId) {
        return "00-%s-%s-01".formatted(traceId, spanId);
    }
}
