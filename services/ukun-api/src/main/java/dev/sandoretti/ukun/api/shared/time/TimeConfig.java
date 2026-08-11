package dev.sandoretti.ukun.api.shared.time;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The clock the application reads the time from — the one {@code shared/package-info.java} has been
 * announcing since v0.2, created by the first feature that could not do without it.
 *
 * <p>Checkout is that feature. An attempt is valid for thirty minutes (ADR-0016), so "has this
 * expired?" is a business rule with a deadline in it, and a rule whose only input is
 * {@code Instant.now()} can be tested in exactly one way: by waiting. With the clock injected, the
 * test that proves a stale attempt answers {@code 410} moves time instead of sleeping through it.
 *
 * <p>{@link Clock#systemUTC()} rather than the default zone, for the same reason
 * {@code OrderSpecifications} interprets its date filters in UTC: the columns are {@code TIMESTAMPTZ}
 * and store instants, and a service whose behaviour depends on the container's {@code TZ} is one
 * whose tests pass in Madrid and fail in CI.
 *
 * <p><strong>Ticking in milliseconds, and that is the interesting half.</strong> A JVM instant carries
 * nanoseconds and a {@code TIMESTAMPTZ} keeps microseconds, so an unrounded timestamp comes back from
 * the database subtly different from the one that was sent — the response of a freshly created order
 * and the response of reading it back would then disagree in their last three digits, for a value the
 * client is expected to compare. Rounding at the source costs nothing here (no rule in this domain
 * measures anything finer than a minute) and makes the round trip lossless.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));
    }
}
