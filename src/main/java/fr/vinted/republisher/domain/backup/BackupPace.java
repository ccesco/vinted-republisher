package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.PauseRange;

import java.time.Duration;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** The pauses between two pages of the backup, varied so that Vinted sees a human rhythm rather than a robot's. */
public final class BackupPace {

    private final PauseRange betweenPages;
    private final RandomGenerator random;

    public BackupPace(PauseRange betweenPages, RandomGenerator random) {
        this.betweenPages = Objects.requireNonNull(betweenPages);
        this.random = Objects.requireNonNull(random);
    }

    public Duration nextPauseBetweenPages() {
        return betweenPages.randomPause(random);
    }
}
