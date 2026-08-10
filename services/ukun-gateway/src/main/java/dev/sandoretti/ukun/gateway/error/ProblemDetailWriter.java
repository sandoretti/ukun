package dev.sandoretti.ukun.gateway.error;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * The only place in this service that builds and writes an error response, so that every one of them
 * comes out with the four fields {@code docs/12-error-contract.md} §1 requires — {@code type},
 * {@code title}, the machine-readable {@code code} and the {@code traceId} — plus the status.
 *
 * <h2>Why the callers cannot pick a code</h2>
 *
 * There is one method per situation instead of a generic {@code write(ErrorCode, …)}, and
 * {@link ErrorCode} is package-private. The registry is closed ({@code docs/12} §4) and this service
 * may emit exactly three of its rows; an API shaped like this cannot produce a fourth by accident,
 * and {@link #payloadTooLarge} cannot forget its {@code maxBytes} extension.
 *
 * <h2>Why it is a bean and not a static helper</h2>
 *
 * It needs the {@code ObjectMapper} Boot autoconfigures — the one carrying Spring's
 * {@code ProblemDetail} mixin, which is what flattens the extensions into top-level JSON members
 * rather than nesting them under {@code properties}. A hand-built mapper would serialise a body the
 * frontend cannot read.
 */
@Component
public class ProblemDetailWriter {

    private static final String CODE = "code";
    private static final String TRACE_ID = "traceId";
    private static final String MAX_BYTES = "maxBytes";

    private final ObjectMapper objectMapper;

    public ProblemDetailWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * No credentials, on a route the coarse filter of ADR-0020 keeps closed. It is deliberately
     * indistinguishable from the 401 {@code ukun-api} would have answered one hop later: the
     * frontend switches on {@code code}, and which of the two services rejected the request is not
     * its business.
     */
    public Mono<Void> unauthenticated(ServerWebExchange exchange) {
        return write(exchange, ErrorCode.UNAUTHENTICATED, "Authentication is required");
    }

    /** Authenticated, wrong role. Same reasoning as {@link #unauthenticated}. */
    public Mono<Void> forbidden(ServerWebExchange exchange) {
        return write(exchange, ErrorCode.FORBIDDEN, "Access to this resource is not allowed");
    }

    /**
     * A body above the limit, cut here rather than at {@code ukun-api}. {@code maxBytes} is the
     * extension the registry declares for this code, and it is what lets the frontend say "5 MB"
     * without hardcoding the number the gateway was configured with.
     */
    public Mono<Void> payloadTooLarge(ServerWebExchange exchange, long maxBytes) {
        ProblemDetail body = problem(ErrorCode.PAYLOAD_TOO_LARGE, "The request body exceeds the size limit");
        body.setProperty(MAX_BYTES, maxBytes);

        return write(exchange, ErrorCode.PAYLOAD_TOO_LARGE, body);
    }

    private Mono<Void> write(ServerWebExchange exchange, ErrorCode code, String detail) {
        return write(exchange, code, problem(code, detail));
    }

    /**
     * The serialisation is deferred so that {@code isCommitted()} is read when the response is
     * actually about to be written, not when the chain is assembled. A committed response can no
     * longer carry a body, and forcing one there would corrupt the one already on the wire.
     */
    private Mono<Void> write(ServerWebExchange exchange, ErrorCode code, ProblemDetail body) {
        ServerHttpResponse response = exchange.getResponse();

        return Mono.defer(() -> {
            if (response.isCommitted()) {
                return response.setComplete();
            }
            response.setStatusCode(code.status());
            response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

            DataBuffer buffer = response.bufferFactory().wrap(objectMapper.writeValueAsBytes(body));

            return response.writeWith(Mono.just(buffer));
        });
    }

    private ProblemDetail problem(ErrorCode code, String detail) {
        HttpStatus status = code.status();
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setType(code.type());
        body.setTitle(code.title());
        body.setProperty(CODE, code.name());
        body.setProperty(TRACE_ID, TraceIds.current());

        return body;
    }
}
