package dev.sandoretti.ukun.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The single entry point of the system (ADR-0003): routing, the coarse role filter and the
 * cross-cutting policies that replace {@code nginx/nginx.conf}.
 *
 * <p>Unlike {@code ukun-api} this is <strong>not</strong> a Spring Modulith application and has no
 * domain of its own — no entities, no repositories, no use cases. Anything that looks like a
 * business rule belongs one hop away, in {@code ukun-api} (skill {@code spring-gateway}).
 *
 * <p>{@code @EnableScheduling} is here rather than next to what uses it, the same as in
 * {@code ukun-auth}: it is a global switch. What uses it is one method,
 * {@code RequestRateLimitBuckets.discardIdleBuckets()}, and it sweeps per-JVM state — so unlike the
 * jobs issue #118 point 4 is about, every replica has to run it.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class UkunGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(UkunGatewayApplication.class, args);
    }
}
