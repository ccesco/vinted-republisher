package fr.vinted.republisher.domain.cadence;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * How fast the tool may write on Vinted: a minimum interval between two writes of any kind, and a limit per kind over
 * the last 24 hours. Computed from the attempts already recorded, it survives any restart.
 */
public record WriteCadence(Duration minimumInterval, int dailyDeletions, int dailyRepublications) {

    private static final Duration WINDOW = Duration.ofHours(24);

    public WriteCadence {
        Objects.requireNonNull(minimumInterval);
        if (minimumInterval.isZero() || minimumInterval.isNegative() || minimumInterval.compareTo(WINDOW) >= 0) {
            // Only the writes of the window are read: a longer interval could not be enforced.
            throw new InvalidWriteCadence("minimum interval " + minimumInterval + " must be positive and under 24 hours");
        }
        if (dailyDeletions <= 0 || dailyRepublications <= 0) {
            throw new InvalidWriteCadence("daily limits must be strictly positive");
        }
    }

    /** Writes attempted at or before this date no longer count against the daily limits. */
    public Instant windowStart(Instant now) {
        return now.minus(WINDOW);
    }

    /**
     * {@code deletions} and {@code republications} are the dates of the recorded attempts, at least those of the
     * window. Throws with the latest date any limit imposes when the write is not allowed now.
     */
    public void check(VintedWrite write, List<Instant> deletions, List<Instant> republications, Instant now) {
        Optional<Instant> afterInterval = Stream.concat(deletions.stream(), republications.stream())
                .max(Instant::compareTo).map(last -> last.plus(minimumInterval));
        Optional<Instant> afterLimit = write == VintedWrite.DELETION
                ? freedPlace(deletions, dailyDeletions, now)
                : freedPlace(republications, dailyRepublications, now);
        Stream.of(afterInterval, afterLimit).flatMap(Optional::stream).filter(now::isBefore)
                .max(Instant::compareTo).ifPresent(allowedAt -> {
                    throw new CadenceLimitReached(allowedAt);
                });
    }

    private Optional<Instant> freedPlace(List<Instant> writes, int limit, Instant now) {
        List<Instant> inWindow = writes.stream().filter(write -> write.isAfter(windowStart(now))).sorted().toList();
        if (inWindow.size() < limit) {
            return Optional.empty();
        }
        return Optional.of(inWindow.get(inWindow.size() - limit).plus(WINDOW));
    }
}
