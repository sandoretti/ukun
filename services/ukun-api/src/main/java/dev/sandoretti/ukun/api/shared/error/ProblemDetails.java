package dev.sandoretti.ukun.api.shared.error;

import org.springframework.http.ProblemDetail;

/**
 * Builds the {@code ProblemDetail} of {@code docs/12-error-contract.md} §1 — {@code type},
 * {@code title}, the machine-readable {@code code} and the {@code traceId}, the four fields no error
 * response of this service may miss.
 *
 * <p>It exists as its own type, rather than as private methods of {@link GlobalExceptionHandler},
 * because the advice is <strong>not</strong> the only place error responses are written. An
 * {@code AuthenticationEntryPoint} rejects a request inside the filter chain, long before any
 * {@code @ExceptionHandler} could see it, and a 401 that came out shaped differently from a 403
 * would be a contract the frontend cannot switch on. Two emitters, one shape, one place that knows
 * how to build it.
 */
public final class ProblemDetails {

    private static final String CODE = "code";
    private static final String TRACE_ID = "traceId";

    private ProblemDetails() {}

    /** A complete response for {@code code}, with the operator-facing {@code detail} of §1. */
    public static ProblemDetail of(ErrorCode code, String detail) {
        return stamp(ProblemDetail.forStatusAndDetail(code.status(), detail), code);
    }

    /** Stamps the four required fields on a body Spring built on its own. */
    public static ProblemDetail stamp(ProblemDetail body, ErrorCode code) {
        body.setType(code.type());
        body.setTitle(code.title());
        body.setProperty(CODE, code.name());
        body.setProperty(TRACE_ID, TraceIds.current());

        return body;
    }

    /** {@code getProperties()} is {@code null} until the first extension is set, hence the guard. */
    static boolean hasCode(ProblemDetail body) {
        var properties = body.getProperties();
        return properties != null && properties.containsKey(CODE);
    }

    /** The {@code traceId} already stamped on {@code body}, for the log line that must match it. */
    static Object traceIdOf(ProblemDetail body) {
        return body.getProperties() == null ? null : body.getProperties().get(TRACE_ID);
    }
}
