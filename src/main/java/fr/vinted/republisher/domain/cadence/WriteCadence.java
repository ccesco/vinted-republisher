package fr.vinted.republisher.domain.cadence;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * How fast the tool may write on Vinted: per kind, a minimum interval since the last write of the same kind (US-46),
 * and a limit over the last 24 hours. Computed from the attempts already recorded, it survives any restart.
 */
public record WriteCadence(Duration deletionInterval, Duration republicationInterval, int dailyDeletions,
                           int dailyRepublications) {

    private static final Duration WINDOW = Duration.ofHours(24);

    public WriteCadence {
        requireUnderWindow(deletionInterval);
        requireUnderWindow(republicationInterval);
        if (dailyDeletions <= 0 || dailyRepublications <= 0) {
            throw new InvalidWriteCadence("daily limits must be strictly positive");
        }
    }

    private static void requireUnderWindow(Duration interval) {
        Objects.requireNonNull(interval);
        if (interval.isZero() || interval.isNegative() || interval.compareTo(WINDOW) >= 0) {
            // Only the writes of the window are read: a longer interval could not be enforced.
            throw new InvalidWriteCadence("minimum interval " + interval + " must be positive and under 24 hours");
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
        Instant allowedAt = allowedAt(write, deletions, republications, now);
        if (now.isBefore(allowedAt)) {
            throw new CadenceLimitReached(allowedAt);
        }
    }

    /** From when the write is allowed, given the same dates as {@link #check}: {@code now} if it is allowed now. */
    public Instant allowedAt(VintedWrite write, List<Instant> deletions, List<Instant> republications, Instant now) {
        boolean deletion = write == VintedWrite.DELETION;
        List<Instant> sameType = deletion ? deletions : republications;
        Optional<Instant> afterInterval = sameType.stream()
                .max(Instant::compareTo)
                .map(last -> last.plus(deletion ? deletionInterval : republicationInterval));
        Optional<Instant> afterLimit = freedPlace(sameType, deletion ? dailyDeletions : dailyRepublications, now);
        return Stream.of(afterInterval, afterLimit).flatMap(Optional::stream).filter(now::isBefore)
                .max(Instant::compareTo).orElse(now);
    }

    private Optional<Instant> freedPlace(List<Instant> writes, int limit, Instant now) {
        List<Instant> inWindow = writes.stream().filter(write -> write.isAfter(windowStart(now))).sorted().toList();
        if (inWindow.size() < limit) {
            return Optional.empty();
        }
        return Optional.of(inWindow.get(inWindow.size() - limit).plus(WINDOW));
    }
}
