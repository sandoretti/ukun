package dev.sandoretti.ukun.auth.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * One token bucket per {@code IP + email}, {@code docs/08-identidad-y-seguridad.md} §8.
 *
 * <p><strong>Why the key is the pair and not just the account.</strong> Counting failures per account
 * turns the protection into the attack: anybody who knows an email can lock its owner out by failing
 * to log in as them. Counting per IP alone punishes everyone behind one NAT. The pair throttles the
 * attempt that is actually repeating.
 *
 * <p><strong>The backoff is progressive because there are two bandwidths, not one.</strong> A person
 * who mistypes their password gets five quick tries and is never aware of this class; a script that
 * keeps going hits the hourly limit and gets a window measured in minutes rather than seconds. One
 * limit alone can only be lenient or hostile, and would have to pick.
 *
 * <p>In memory, which is correct for exactly as long as there is one replica — the same assumption
 * ADR-0003 makes for the gateway's session. A second replica needs a shared store, and Bucket4j has
 * a distributed {@code ProxyManager} for that: the day it matters, this class changes and the filter
 * does not.
 */
@Component
public class LoginAttemptBuckets {

    /** Room for a person who mistypes, none for a script. */
    private static final Bandwidth BURST = Bandwidth.builder()
            .capacity(5)
            .refillIntervally(5, Duration.ofMinutes(1))
            .build();

    /** The one that actually bites: sustained guessing runs out of budget for the hour. */
    private static final Bandwidth SUSTAINED = Bandwidth.builder()
            .capacity(20)
            .refillIntervally(20, Duration.ofHours(1))
            .build();

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public Bucket forAttempt(String ip, String email) {
        return buckets.computeIfAbsent(keyOf(ip, email), key -> newBucket());
    }

    private static Bucket newBucket() {
        return Bucket.builder().addLimit(BURST).addLimit(SUSTAINED).build();
    }

    private static String keyOf(String ip, String email) {
        // Lower-cased because the column is CITEXT and the two spellings are one account — without
        // this, alternating the capitalisation of an email would double the budget.
        return ip + "|" + (email == null ? "" : email.toLowerCase());
    }

    /**
     * Without this the map grows once per distinct {@code IP + email} seen and never shrinks, which
     * is a slow memory leak with an attacker-controlled key — the rate limiter would become the
     * denial of service it exists to prevent.
     *
     * <p>A bucket back at full capacity has no history worth keeping: dropping it and recreating it
     * on the next attempt is indistinguishable from having kept it.
     */
    @Scheduled(fixedDelay = 10, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    void discardIdleBuckets() {
        buckets.values().removeIf(bucket -> bucket.getAvailableTokens() >= SUSTAINED.getCapacity());
    }
}
