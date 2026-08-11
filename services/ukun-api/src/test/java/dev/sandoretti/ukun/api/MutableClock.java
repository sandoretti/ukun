package dev.sandoretti.ukun.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The clock the checkout suites run on: it starts at the real time and only moves when a test says so.
 *
 * <p>It exists for one assertion — a checkout attempt is valid for thirty minutes (ADR-0016), and the
 * only other way to prove that a stale one answers {@code 410} is to wait half an hour or to rewrite
 * two timestamps behind the service's back. The first is not a test anybody runs; the second asserts
 * that the database can be edited, not that the rule works.
 *
 * <p>It ticks in milliseconds like the real one ({@code TimeConfig}), so an instant survives the round
 * trip through a {@code TIMESTAMPTZ} unchanged and a test may compare what the API returned with what
 * it stored.
 *
 * <p>Standing still between calls has a second effect worth having: two orders placed in the same test
 * share a {@code placed_at} to the microsecond, which is the tie defect #23 is about. The history
 * suite gets that situation for free instead of having to contrive it.
 */
public final class MutableClock extends Clock {

    private final ZoneId zone;
    private Instant instant;

    public MutableClock() {
        this(ZoneOffset.UTC, Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    private MutableClock(ZoneId zone, Instant instant) {
        this.zone = zone;
        this.instant = instant;
    }

    /** Back to the real time. Called before each test, so no suite inherits another's travelling. */
    public void reset() {
        this.instant = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    public void advance(Duration amount) {
        this.instant = instant.plus(amount);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId other) {
        return new MutableClock(other, instant);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
