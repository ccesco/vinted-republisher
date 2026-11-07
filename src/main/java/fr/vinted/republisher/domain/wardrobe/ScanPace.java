package fr.vinted.republisher.domain.wardrobe;

import java.time.Duration;
import java.util.random.RandomGenerator;

/** The pauses of a scan, varied so that Vinted sees a human rhythm rather than a robot's. */
public final class ScanPace {

    private final PauseRange betweenScrolls;
    private final PauseRange betweenListings;
    private final RandomGenerator random;

    public ScanPace(PauseRange betweenScrolls, PauseRange betweenListings, RandomGenerator random) {
        this.betweenScrolls = betweenScrolls;
        this.betweenListings = betweenListings;
        this.random = random;
    }

    public Duration nextPauseBetweenScrolls() {
        return nextPause(betweenScrolls);
    }

    public Duration nextPauseBetweenListings() {
        return nextPause(betweenListings);
    }

    private Duration nextPause(PauseRange range) {
        return Duration.ofMillis(random.nextLong(range.minimum().toMillis(), range.maximum().toMillis() + 1));
    }
}
