package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.PauseRange;

import java.time.Duration;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** The pauses between two listing forms, varied so that Vinted sees a human rhythm rather than a robot's. */
public final class BackupPace {

    private final PauseRange betweenListings;
    private final RandomGenerator random;

    public BackupPace(PauseRange betweenListings, RandomGenerator random) {
        this.betweenListings = Objects.requireNonNull(betweenListings);
        this.random = Objects.requireNonNull(random);
    }

    public Duration nextPauseBetweenListings() {
        return betweenListings.randomPause(random);
    }
}
