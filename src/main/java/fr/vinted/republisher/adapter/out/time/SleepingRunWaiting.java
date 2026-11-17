package fr.vinted.republisher.adapter.out.time;

import fr.vinted.republisher.domain.run.RunWaiting;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

/** Waits by putting the run's background thread to sleep by short steps, checking between them for a pause or a stop. */
public class SleepingRunWaiting implements RunWaiting {

    private final Clock clock;
    private final Duration checkEvery;

    /** {@code checkEvery}: at most a second, the delay the member waits for a pause or a stop to be seen. */
    public SleepingRunWaiting(Clock clock, Duration checkEvery) {
        if (!checkEvery.isPositive() || checkEvery.compareTo(Duration.ofSeconds(1)) > 0) {
            throw new IllegalArgumentException("Check every " + checkEvery + ": must be positive, at most a second");
        }
        this.clock = clock;
        this.checkEvery = checkEvery;
    }

    @Override
    public void until(Instant instant, BooleanSupplier interrupted) {
        Duration left = Duration.between(clock.instant(), instant);
        while (left.isPositive() && !interrupted.getAsBoolean()) {
            sleep(left.compareTo(checkEvery) < 0 ? left : checkEvery);
            left = Duration.between(clock.instant(), instant);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Automatic run interrupted while waiting", interrupted);
        }
    }
}
