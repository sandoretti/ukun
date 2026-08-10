package dev.sandoretti.ukun.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The single entry point of the system (ADR-0003): routing, the coarse role filter and the
 * cross-cutting policies that replace {@code nginx/nginx.conf}.
 *
 * <p>Unlike {@code ukun-api} this is <strong>not</strong> a Spring Modulith application and has no
 * domain of its own — no entities, no repositories, no use cases. Anything that looks like a
 * business rule belongs one hop away, in {@code ukun-api} (skill {@code spring-gateway}).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class UkunGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(UkunGatewayApplication.class, args);
    }
}
