package dev.sandoretti.ukun.api.orders;

import dev.sandoretti.ukun.api.MutableClock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the application's clock with one the checkout suites move ({@link MutableClock}).
 *
 * <p><strong>{@code @Primary}, and the bean is deliberately not called {@code clock}.</strong>
 * {@code TimeConfig} already defines one under that name and bean overriding is off by default, so a
 * second definition of the same name would fail the context outright. Two candidates with this one
 * preferred is what Spring is for, and it leaves the production configuration untouched.
 *
 * <p>A top-level class rather than one nested in {@code OrdersIntegrationTest}, and that is not a
 * style choice: a nested {@code @TestConfiguration} is only detected on the class carrying the
 * annotations, so declared inside the abstract fixture it was silently ignored and every subclass
 * failed to autowire the clock. It is named in {@code @SpringBootTest(classes = …)} instead, which
 * Spring Boot merges with the application's own configuration precisely because every class listed
 * there is a {@code @TestConfiguration}.
 */
@TestConfiguration
public class ControllableTime {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock();
    }
}
