package fr.vinted.republisher.fakes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Time moves only when a test says so, or by a step each time it is read. */
public class AdjustableClock extends Clock {

    private Instant now;
    private Duration step = Duration.ZERO;

    public AdjustableClock(Instant now) {
        this.now = now;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    public AdjustableClock advancingAtEachReadingBy(Duration duration) {
        step = duration;
        return this;
    }

    @Override
    public Instant instant() {
        Instant current = now;
        now = now.plus(step);
        return current;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
