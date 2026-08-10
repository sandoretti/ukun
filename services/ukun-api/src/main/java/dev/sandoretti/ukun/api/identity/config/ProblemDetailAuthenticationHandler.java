package dev.sandoretti.ukun.api.identity.config;

import tools.jackson.databind.ObjectMapper;
import dev.sandoretti.ukun.api.shared.error.ErrorCode;
import dev.sandoretti.ukun.api.shared.error.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Writes the {@code 401} and the {@code 403} that are decided <strong>inside the filter chain</strong>
 * — a missing token, one the decoder rejects, a route the caller's roles do not open.
 *
 * <p>It exists because those never reach the {@code @RestControllerAdvice}: the request is turned away
 * before any handler method is selected, so there is no {@code @ExceptionHandler} to run. Left to
 * Spring Security's defaults they would come back as an empty body with a {@code WWW-Authenticate}
 * header, and the frontend — which switches on {@code code} and nothing else
 * ({@code docs/12-error-contract.md} §1) — would have nothing to read.
 *
 * <p>Both methods build the body with the same {@link ProblemDetails} the advice uses, so a
 * {@code 403} from a route rule and a {@code 403} from a {@code @PreAuthorize} are indistinguishable
 * to the client. That is the point: which of the two internal paths rejected the request is this
 * service's business, not the caller's.
 *
 * <p>It is wired as a {@code @Bean} of {@link SecurityConfig} rather than as a {@code @Component}
 * because it is part of that configuration: whoever imports the filter chain gets the two handlers
 * that give it its shape, and no test can end up asserting a chain assembled with Spring Security's
 * empty-bodied defaults.
 *
 * <p>Neither response says <em>why</em>. "No token", "expired", "wrong audience" and "signature does
 * not verify" all come back as {@code UNAUTHENTICATED}: a 401 that explains itself is an oracle, and
 * the detail belongs in the log, never in the response ({@code docs/08-identidad-y-seguridad.md} §8
 * makes the same call for the login endpoint).
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

    /** Authenticated, but this route is not open to these roles. */
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
