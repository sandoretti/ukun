package dev.sandoretti.ukun.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} is here and not next to what uses it because it is a global switch, and
 * the root package is outside every application module (ADR-0017 §4). Today it drives one job: the
 * sweep that keeps the login rate limiter's map from growing once per distinct IP and email and
 * never shrinking. The reconciliation of accounts still disabled after 24 hours (skill
 * {@code spring-auth}, rule 7) joins it in issue #25.
 */
@EnableScheduling
@SpringBootApplication
public class UkunAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(UkunAuthApplication.class, args);
    }
}
