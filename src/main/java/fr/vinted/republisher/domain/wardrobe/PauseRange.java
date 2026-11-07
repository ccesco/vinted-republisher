package fr.vinted.republisher.domain.wardrobe;

import java.time.Duration;
import java.util.Objects;
import java.util.random.RandomGenerator;

public record PauseRange(Duration minimum, Duration maximum) {

    public PauseRange {
        Objects.requireNonNull(minimum);
        Objects.requireNonNull(maximum);
        if (minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("Minimum pause " + minimum + " above maximum " + maximum);
        }
    }

    public Duration randomPause(RandomGenerator random) {
        return Duration.ofMillis(random.nextLong(minimum.toMillis(), maximum.toMillis() + 1));
    }
}
