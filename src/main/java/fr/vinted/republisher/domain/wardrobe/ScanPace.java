package fr.vinted.republisher.domain.wardrobe;

import java.time.Duration;
import java.util.random.RandomGenerator;

/** The pauses of a reading, varied so that Vinted sees a human rhythm rather than a robot's. */
public final class ScanPace {

    private final Duration minimum;
    private final Duration maximum;
    private final RandomGenerator random;

    public ScanPace(Duration minimum, Duration maximum, RandomGenerator random) {
        if (minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("Minimum pause " + minimum + " above maximum " + maximum);
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.random = random;
    }

    public Duration nextPause() {
        return Duration.ofMillis(random.nextLong(minimum.toMillis(), maximum.toMillis() + 1));
    }
}
