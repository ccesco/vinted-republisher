package fr.vinted.republisher.domain.run;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The automatic run as the member follows it: its status and dates, the listing being relaunched, the relaunches done,
 * the listings set aside for the member to decide, and the pause or stop the member asked for, which takes effect
 * once the current relaunch is over. Kept in memory: the actions themselves are in the journal.
 */
public record AutomaticRun(RunStatus status, Optional<Instant> startedAt, Optional<Instant> endedAt,
                           Optional<Instant> waitingUntil, Optional<VintedListingId> currentListing,
                           int relaunchCount, List<VintedListingId> skippedListings,
                           Optional<RunStopReason> stopReason, boolean pauseRequested, boolean stopRequested) {

    public AutomaticRun {
        Objects.requireNonNull(status);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(endedAt);
        Objects.requireNonNull(waitingUntil);
        Objects.requireNonNull(currentListing);
        skippedListings = List.copyOf(skippedListings);
        Objects.requireNonNull(stopReason);
    }

    public static AutomaticRun idle() {
        return new AutomaticRun(RunStatus.IDLE, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), 0, List.of(), Optional.empty(), false, false);
    }

    public static AutomaticRun startedAt(Instant at) {
        return new AutomaticRun(RunStatus.RUNNING, Optional.of(at), Optional.empty(), Optional.empty(),
                Optional.empty(), 0, List.of(), Optional.empty(), false, false);
    }

    /** Running, waiting or paused: a new launch is ignored, a pause or a stop can be asked for. */
    public boolean inProgress() {
        return status == RunStatus.RUNNING || status == RunStatus.WAITING || status == RunStatus.PAUSED;
    }

    public boolean skips(VintedListingId listing) {
        return skippedListings.contains(listing);
    }

    public AutomaticRun relaunching(VintedListingId listing) {
        return new AutomaticRun(RunStatus.RUNNING, startedAt, endedAt, Optional.empty(), Optional.of(listing),
                relaunchCount, skippedListings, stopReason, pauseRequested, stopRequested);
    }

    public AutomaticRun relaunched() {
        return new AutomaticRun(status, startedAt, endedAt, waitingUntil, currentListing, relaunchCount + 1,
                skippedListings, stopReason, pauseRequested, stopRequested);
    }

    public AutomaticRun skipping(VintedListingId listing) {
        List<VintedListingId> skipped = new ArrayList<>(skippedListings);
        skipped.add(listing);
        return new AutomaticRun(status, startedAt, endedAt, waitingUntil, currentListing, relaunchCount, skipped,
                stopReason, pauseRequested, stopRequested);
    }

    public AutomaticRun waitingUntil(Instant until) {
        return new AutomaticRun(RunStatus.WAITING, startedAt, endedAt, Optional.of(until), currentListing,
                relaunchCount, skippedListings, stopReason, pauseRequested, stopRequested);
    }

    /** Ignored unless the run is in progress and not paused already. */
    public AutomaticRun withPauseRequested() {
        if (!inProgress() || status == RunStatus.PAUSED) {
            return this;
        }
        return new AutomaticRun(status, startedAt, endedAt, waitingUntil, currentListing, relaunchCount,
                skippedListings, stopReason, true, stopRequested);
    }

    /** A resume asked for since the pause was seen cancels it: the run goes on. */
    public AutomaticRun pausedIfRequested() {
        if (!pauseRequested) {
            return this;
        }
        return new AutomaticRun(RunStatus.PAUSED, startedAt, endedAt, Optional.empty(), currentListing,
                relaunchCount, skippedListings, stopReason, false, stopRequested);
    }

    /** Ignored unless the run is paused, or a pause is asked for. */
    public AutomaticRun resumed() {
        if (status != RunStatus.PAUSED && !pauseRequested) {
            return this;
        }
        RunStatus resumed = status == RunStatus.PAUSED ? RunStatus.RUNNING : status;
        return new AutomaticRun(resumed, startedAt, endedAt, waitingUntil, currentListing, relaunchCount,
                skippedListings, stopReason, false, stopRequested);
    }

    /** Ignored unless the run is in progress. */
    public AutomaticRun withStopRequested() {
        if (!inProgress()) {
            return this;
        }
        return new AutomaticRun(status, startedAt, endedAt, waitingUntil, currentListing, relaunchCount,
                skippedListings, stopReason, pauseRequested, true);
    }

    public AutomaticRun finishedAt(Instant at) {
        return new AutomaticRun(RunStatus.FINISHED, startedAt, Optional.of(at), Optional.empty(), Optional.empty(),
                relaunchCount, skippedListings, stopReason, false, false);
    }

    public AutomaticRun stoppedAt(Instant at, RunStopReason reason) {
        return new AutomaticRun(RunStatus.STOPPED, startedAt, Optional.of(at), Optional.empty(), Optional.empty(),
                relaunchCount, skippedListings, Optional.of(reason), false, false);
    }
}
