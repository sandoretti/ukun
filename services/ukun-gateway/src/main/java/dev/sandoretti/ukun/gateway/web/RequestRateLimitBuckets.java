package dev.sandoretti.ukun.gateway.web;

import dev.sandoretti.ukun.gateway.config.UkunGatewayProperties;
import dev.sandoretti.ukun.gateway.config.UkunGatewayProperties.RateLimit;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * One token bucket per {@code tier + client}, {@code docs/08-identidad-y-seguridad.md} §8.
 *
 * <h2>Two tiers and not one</h2>
 *
 * The same argument {@code LoginAttemptBuckets} makes in {@code ukun-auth}: a single budget can only
 * be lenient or hostile and would have to pick. {@link Tier#API} is sized so that somebody browsing
 * the shop never meets it; {@link Tier#SIGNUP} is sized for an operation a person performs once.
 *
 * <p>A request consumes from <em>one</em> tier, the most specific that matches. Charging both would
 * change nothing — the stricter budget runs out first by construction — and would make a 429 on
 * signup silently eat into the budget of the catalogue the same person is browsing.
 *
 * <h2>In memory, and correct for exactly one replica</h2>
 *
 * The same assumption ADR-0003 makes for the gateway's session and {@code LoginAttemptBuckets} for
 * the login budget: with N replicas behind a balancer the real budget is N times this one. Bucket4j
 * has a distributed {@code ProxyManager} for the day that matters, and that day is issue #118 — which
 * also owns the other half nobody can skip, {@code forward-headers-strategy} with a trusted proxy,
 * because a shared bucket keyed on the wrong address limits nothing at all.
 */
@Component
public class RequestRateLimitBuckets {

    /** Which budget a request is charged to. The filter decides; this class only holds them. */
    public enum Tier {
        API,
        SIGNUP
    }

    private final Map<Tier, Bandwidth> bandwidths;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    RequestRateLimitBuckets(UkunGatewayProperties properties) {
        RateLimit limits = properties.rateLimit();
        this.bandwidths = new EnumMap<>(Tier.class);
        this.bandwidths.put(Tier.API, bandwidthOf(limits.api()));
        this.bandwidths.put(Tier.SIGNUP, bandwidthOf(limits.signup()));
    }

    Bucket forClient(Tier tier, String client) {
        return buckets.computeIfAbsent(tier.name() + "|" + client, key -> Bucket.builder()
                .addLimit(bandwidths.get(tier))
                .build());
    }

    /**
     * Without this the map grows once per distinct {@code tier + client} seen and never shrinks, with
     * a key an attacker chooses — the rate limiter would become the denial of service it exists to
     * prevent. A bucket back at full capacity has no history worth keeping: dropping it and building
     * it again on the next request is indistinguishable from having kept it.
     *
     * <p>This is per-JVM state, so every replica has to run it. That is the opposite of the concern
     * issue #118 raises about {@code @Scheduled} jobs — nothing here is shared work being duplicated.
     */
    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    void discardIdleBuckets() {
        buckets.entrySet()
                .removeIf(entry -> entry.getValue().getAvailableTokens() >= capacityOf(entry.getKey()));
    }

    /**
     * Hands every budget back. It exists for {@code RateLimitIT}: the buckets are per-JVM state that
     * outlives a test method inside a shared application context, so a test that runs a budget out
     * has to put it back or the next class starts throttled.
     *
     * <p>There is no production caller and there must not be one — a request that could reset the
     * limiter would be the limiter's own bypass, which is why the reset is a method on the bean and
     * never an endpoint.
     */
    public void discardAll() {
        buckets.clear();
    }

    private long capacityOf(String bucketKey) {
        return bandwidths.get(Tier.valueOf(bucketKey.substring(0, bucketKey.indexOf('|')))).getCapacity();
    }

    private static Bandwidth bandwidthOf(RateLimit.Tier tier) {
        return Bandwidth.builder()
                .capacity(tier.capacity())
                .refillIntervally(tier.capacity(), tier.window())
                .build();
    }
}
