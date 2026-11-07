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
        return betweenScrolls.randomPause(random);
    }

    public Duration nextPauseBetweenListings() {
        return betweenListings.randomPause(random);
    }
}
