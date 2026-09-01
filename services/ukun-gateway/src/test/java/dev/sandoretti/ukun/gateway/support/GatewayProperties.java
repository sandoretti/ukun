package dev.sandoretti.ukun.gateway.support;

import dev.sandoretti.ukun.gateway.config.UkunGatewayProperties;
import dev.sandoretti.ukun.gateway.config.UkunGatewayProperties.RateLimit;
import dev.sandoretti.ukun.gateway.config.UkunGatewayProperties.RateLimit.Tier;
import java.net.URI;
import java.time.Duration;
import org.springframework.util.unit.DataSize;

/**
 * Values for the parts of {@code UkunGatewayProperties} a unit test has to pass and does not care
 * about.
 *
 * <p>It exists so that adding a property to that record does not send every filter test off to
 * invent a plausible value for it: a test whose subject is the body size limit should say so by
 * naming one number and borrowing the rest from here.
 */
public final class GatewayProperties {

    private GatewayProperties() {}

    /** The whole record, with everything but the budgets set to a value no test asserts on. */
    public static UkunGatewayProperties with(RateLimit rateLimit) {
        return new UkunGatewayProperties(
                URI.create("http://ukun-api:8081"),
                URI.create("http://ukun-auth:9000"),
                URI.create("http://ukun-web:5173"),
                "file:/opt/app/web/",
                DataSize.ofMegabytes(5),
                "default-src 'self'",
                rateLimit);
    }

    /** Budgets nothing in a unit test will run out of. */
    public static RateLimit anyRateLimit() {
        return rateLimit(1_000, 1_000);
    }

    /** Budgets a test picks on purpose, both refilling once a minute. */
    public static RateLimit rateLimit(int apiCapacity, int signupCapacity) {
        return new RateLimit(
                new Tier(apiCapacity, Duration.ofMinutes(1)), new Tier(signupCapacity, Duration.ofMinutes(1)));
    }
}
