package dev.sandoretti.ukun.auth.config;

import dev.sandoretti.ukun.auth.error.ErrorCode;
import dev.sandoretti.ukun.auth.error.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the {@code 401} and the {@code 403} of the {@code /internal/**} chain, which are decided
 * <strong>inside the filter chain</strong> — no token, a token the decoder rejects, or a client
 * without the {@code accounts:write} scope.
 *
 * <p>Those never reach the {@code @RestControllerAdvice}: the request is turned away before a handler
 * method is selected, so there is no {@code @ExceptionHandler} to run. Left to Spring Security's
 * defaults they come back as an empty body, and ukun-api's adapter — which branches on status and
 * whose caller branches on {@code code} — would have nothing to read.
 *
 * <p>Neither response says <em>why</em>. "No token", "expired", "wrong audience" and "missing scope"
 * all come back the same way, for the reason {@code docs/08-identidad-y-seguridad.md} §8 gives about
 * the login endpoint: a response that explains itself is an oracle.
 *
 * <p>It deliberately does not apply to the browser-facing chain. There, an unauthenticated request
 * must be redirected to the login form, not answered with JSON — which is why
 * {@link AuthorizationServerConfig} keeps its own entry point.
 */
class ProblemDetailAuthenticationHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    ProblemDetailAuthenticationHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** No credentials, or credentials this service does not accept. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {

        write(response, ErrorCode.UNAUTHENTICATED, "Authentication is required");
    }

    /** Authenticated as a client, but without the scope these operations require. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {

        write(response, ErrorCode.FORBIDDEN, "Access to this resource is not allowed");
    }

    private void write(HttpServletResponse response, ErrorCode code, String detail) throws IOException {
        ProblemDetail body = ProblemDetails.of(code, detail);

        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
