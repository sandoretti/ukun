package dev.sandoretti.ukun.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} is here and not next to the one job that needs it, for the reason
 * {@code ukun-auth} states on its own application class: it is a global switch, and a global switch
 * reads better where the application is defined than inside a feature.
 *
 * <p>This service ran without any scheduled work until v1.0. What turned it on is
 * {@code CheckoutPurgeJob}, the daily purge that {@code docs/10} §4 assumed existed when it decided
 * the two checkout tables need no index (ADR-0024, issue #118).
 */
@EnableScheduling
@SpringBootApplication
public class UkunApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(UkunApiApplication.class, args);
    }
}
