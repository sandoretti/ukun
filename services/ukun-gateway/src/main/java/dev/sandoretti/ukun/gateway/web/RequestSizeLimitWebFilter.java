package dev.sandoretti.ukun.gateway.web;

import dev.sandoretti.ukun.gateway.config.UkunGatewayProperties;
import dev.sandoretti.ukun.gateway.error.ProblemDetailWriter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * The body size limit the legacy never really had. Defect #17 of
 * {@code docs/03-analisis-y-patrones.md} §1 is not "the limit was too high": it is that Spring said
 * 10 MB, nginx declared no {@code client_max_body_size} at all, and the effective limit was neither
 * of the two. Here it is 5 MB, the same value as {@code spring.servlet.multipart.max-file-size} in
 * {@code ukun-api}, and the two agreeing is the whole point ({@code docs/08} §9).
 *
 * <p>The upload endpoint it protects ({@code PUT /api/admin/products/{id}/image}) arrives in v0.7.
 * The limit goes in now because this is the release where the gateway takes over from nginx, and a
 * limit added later is a limit that was missing in between.
 *
 * <h2>Why not {@code RequestSizeGatewayFilterFactory}</h2>
 *
 * Spring Cloud Gateway ships one, and it answers 413 with an {@code errorMessage} header and an
 * <em>empty body</em>. {@code docs/12-error-contract.md} §2 says {@code PAYLOAD_TOO_LARGE} is the
 * one code of the registry with two possible emitters and that both must produce the same shape,
 * because the frontend cannot tell which service cut the request. An empty body is not that shape.
 *
 * <h2>Why {@code @Order(0)} — after the security chain, not before</h2>
 *
 * The instinct is to reject early. It is wrong here: {@code WebFilterChainProxy} is
 * {@code @Order(-100)}, and the four security headers are written from inside it, through a
 * {@code beforeCommit} hook registered by {@code HttpHeaderWriterWebFilter}. A filter that answered
 * 413 before that would emit it without a single security header — breaking one requirement to meet
 * another. Running at 0 puts this after the whole security chain and still before the
 * {@code DispatcherHandler}, so nothing is proxied, an oversized request to {@code /api/admin/**}
 * without the role is still answered 401 first, and the 413 comes out fully dressed. It costs
 * nothing: in WebFlux the body is not buffered until somebody reads it, and the one who reads it is
 * the routing filter, much later.
 *
 * <h2>What this deliberately does not do</h2>
 *
 * It checks {@code Content-Length} and nothing else. Counting bytes to catch a
 * {@code Transfer-Encoding: chunked} body would mean aborting mid-stream, by which point the routing
 * filter has already opened the exchange with the upstream and the response may be committed — the
 * contract would become "413 sometimes, a connection reset otherwise", which is worse than a
 * documented limit. No browser sends a {@code FormData} upload chunked, and the case that does slip
 * through lands on {@code spring.servlet.multipart.max-file-size} in {@code ukun-api}, which answers
 * the same {@code ProblemDetail} — the second emitter {@code docs/12} already provides for.
 */
@Component
@Order(0)
class RequestSizeLimitWebFilter implements WebFilter {

    private final long maxBytes;
    private final ProblemDetailWriter problems;

    RequestSizeLimitWebFilter(UkunGatewayProperties properties, ProblemDetailWriter problems) {
        this.maxBytes = properties.maxBodySize().toBytes();
        this.problems = problems;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // -1 when the header is absent or unparseable, which is the chunked case above.
        if (exchange.getRequest().getHeaders().getContentLength() > maxBytes) {
            return problems.payloadTooLarge(exchange, maxBytes);
        }
        return chain.filter(exchange);
    }
}
