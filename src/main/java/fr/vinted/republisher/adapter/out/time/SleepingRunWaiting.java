package fr.vinted.republisher.adapter.out.time;

import fr.vinted.republisher.domain.run.RunWaiting;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Waits by putting the run's background thread to sleep until the date. */
public class SleepingRunWaiting implements RunWaiting {

    private final Clock clock;

    public SleepingRunWaiting(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void until(Instant instant) {
        Duration left = Duration.between(clock.instant(), instant);
        if (left.isNegative() || left.isZero()) {
            return;
        }
        try {
            Thread.sleep(left);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Automatic run interrupted while waiting", interrupted);
        }
    }
}
