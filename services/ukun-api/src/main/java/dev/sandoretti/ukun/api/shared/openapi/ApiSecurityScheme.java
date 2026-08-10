package dev.sandoretti.ukun.api.shared.openapi;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Declares {@code bearerAuth}, the one security scheme of {@code docs/api/openapi/ukun-api.yaml}, so
 * that a protected operation can reference it by the name the contract gives it.
 *
 * <p>It exists because springdoc cannot infer the scheme from the resource server configuration: it
 * reads controllers, and what makes a route need a token lives in {@code SecurityConfig}'s filter
 * chain. Referencing it is per operation — a class-level {@code @SecurityRequirement} on each protected
 * controller — rather than a global default, deliberately: with a default, forgetting the annotation
 * would publish a public endpoint as protected, which is the mistake that reads as correct. As it is,
 * the miss goes the other way and {@code OpenApiDriftIT} catches it.
 *
 * <p>The description is the contract's, shortened. <strong>From the browser nobody sends this token</strong>:
 * the SPA holds an {@code HttpOnly} session cookie against ukun-gateway and the gateway relays the
 * {@code Bearer} (ADR-0003). The scheme is declared because it describes the real boundary of this
 * service, which is the final authorisation authority and never trusts a header the gateway injected.
 */
@Configuration
@SecurityScheme(
        name = ApiSecurityScheme.BEARER_AUTH,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Access token issued by ukun-auth and validated by this service against its JWKS.")
public class ApiSecurityScheme {

    /** The name the contract uses; an operation's {@code @SecurityRequirement} must match it exactly. */
    public static final String BEARER_AUTH = "bearerAuth";
}
