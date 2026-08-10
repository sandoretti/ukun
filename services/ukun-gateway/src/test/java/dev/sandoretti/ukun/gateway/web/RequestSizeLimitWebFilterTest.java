package dev.sandoretti.ukun.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import dev.sandoretti.ukun.gateway.config.UkunGatewayProperties;
import dev.sandoretti.ukun.gateway.error.ProblemDetailWriter;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The body size limit, without a server. What is under test is a decision about one header and the
 * shape of the response that follows from it — {@code docs/12-error-contract.md} calls
 * {@code PAYLOAD_TOO_LARGE} the one code two services may emit, and the frontend cannot tell them
 * apart, so the shape is the requirement.
 *
 * <p>The mapper is built with Spring's own {@code ProblemDetail} mixin, which is what flattens the
 * extensions into top-level members instead of nesting them under {@code properties}. Boot's
 * autoconfigured mapper carries it; a bare {@code JsonMapper} does not, and would let this test pass
 * against a body the SPA cannot read.
 */
class RequestSizeLimitWebFilterTest {

    private static final long MAX_BYTES = DataSize.ofMegabytes(5).toBytes();
    private static final String UPLOAD = "/api/admin/products/42/image";

    private final AtomicBoolean forwarded = new AtomicBoolean();
    private final WebFilterChain chain = exchange -> {
        forwarded.set(true);
        return Mono.empty();
    };
    private final RequestSizeLimitWebFilter filter = new RequestSizeLimitWebFilter(properties(), writer());

    @Test
    @DisplayName("rejects a body above the limit with the ProblemDetail of the contract")
    void rejectsABodyAboveTheLimit() {
        MockServerWebExchange exchange = exchangeOf(MAX_BYTES + 1);

        filter.filter(exchange, chain).block();

        String body = exchange.getResponse().getBodyAsString().block();
        assertSoftly(softly -> {
            softly.assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
            softly.assertThat(exchange.getResponse().getHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            softly.assertThat(body)
                    .contains("\"type\":\"https://ukun.dev/errors/payload-too-large\"")
                    .contains("\"title\":\"Payload too large\"")
                    .contains("\"status\":413")
                    .contains("\"code\":\"PAYLOAD_TOO_LARGE\"")
                    .contains("\"maxBytes\":%d".formatted(MAX_BYTES))
                    .containsPattern("\"traceId\":\"[0-9a-f]{32}\"");
            softly.assertThat(forwarded).isFalse();
        });
    }

    @Test
    @DisplayName("forwards a body at the limit")
    void forwardsABodyAtTheLimit() {
        MockServerWebExchange exchange = exchangeOf(MAX_BYTES);

        filter.filter(exchange, chain).block();

        assertThat(forwarded).isTrue();
    }

    /**
     * The documented gap: a chunked body carries no {@code Content-Length} and is forwarded, to be
     * cut by {@code spring.servlet.multipart.max-file-size} in {@code ukun-api} — the second emitter
     * {@code docs/12} §2 provides for. Fixed as a test so that "it passes through" stays a decision
     * rather than becoming a surprise.
     */
    @Test
    @DisplayName("forwards a request with no Content-Length instead of guessing")
    void forwardsARequestWithNoContentLength() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(UPLOAD));

        assertThatCode(() -> filter.filter(exchange, chain).block()).doesNotThrowAnyException();

        assertThat(forwarded).isTrue();
    }

    private static MockServerWebExchange exchangeOf(long contentLength) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.post(UPLOAD).header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength)));
    }

    private static UkunGatewayProperties properties() {
        return new UkunGatewayProperties(
                URI.create("http://ukun-api:8081"),
                URI.create("http://ukun-auth:9000"),
                URI.create("http://ukun-web:5173"),
                "file:/opt/app/web/",
                DataSize.ofBytes(MAX_BYTES),
                "default-src 'self'");
    }

    private static ProblemDetailWriter writer() {
        ObjectMapper mapper = JsonMapper.builder()
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
                .build();

        return new ProblemDetailWriter(mapper);
    }
}
