package fr.vinted.republisher.domain.run;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The automatic run as the member follows it: its status and dates, the listings of the last round (relaunched
 * together, US-15), the relaunches done, the listings set aside for the member to decide, and the pause or stop the
 * member asked for, which takes effect once the current round is over. Kept in memory: the actions are in the journal.
 */
public record AutomaticRun(RunStatus status, Optional<Instant> startedAt, Optional<Instant> endedAt,
                           Optional<Instant> waitingUntil, List<VintedListingId> currentListings,
                           int relaunchCount, List<VintedListingId> skippedListings,
                           Optional<RunStopReason> stopReason, boolean pauseRequested, boolean stopRequested) {

    public AutomaticRun {
        Objects.requireNonNull(status);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(endedAt);
        Objects.requireNonNull(waitingUntil);
        currentListings = List.copyOf(currentListings);
        skippedListings = List.copyOf(skippedListings);
        Objects.requireNonNull(stopReason);
    }

    public static AutomaticRun idle() {
        return new AutomaticRun(RunStatus.IDLE, Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(), 0, List.of(), Optional.empty(), false, false);
    }

    public static AutomaticRun startedAt(Instant at) {
        return new AutomaticRun(RunStatus.RUNNING, Optional.of(at), Optional.empty(), Optional.empty(),
                List.of(), 0, List.of(), Optional.empty(), false, false);
    }

    /** Running, waiting or paused: a new launch is ignored, a pause or a stop can be asked for. */
    public boolean inProgress() {
        return status == RunStatus.RUNNING || status == RunStatus.WAITING || status == RunStatus.PAUSED;
    }

    public boolean skips(VintedListingId listing) {
        return skippedListings.contains(listing);
    }

    /**
     * Whether the run will not take the listing again: set aside by a run in progress, or relaunched in the round
     * under way (US-41).
     */
    public boolean leavesOut(VintedListingId listing) {
        return inProgress() && skips(listing) || status == RunStatus.RUNNING && currentListings.contains(listing);
    }

    public AutomaticRun relaunching(List<VintedListingId> listings) {
        return new AutomaticRun(RunStatus.RUNNING, startedAt, endedAt, Optional.empty(), listings,
                relaunchCount, skippedListings, stopReason, pauseRequested, stopRequested);
    }

    public AutomaticRun relaunched() {
        return new AutomaticRun(status, startedAt, endedAt, waitingUntil, currentListings, relaunchCount + 1,
                skippedListings, stopReason, pauseRequested, stopRequested);
    }

    public AutomaticRun skipping(VintedListingId listing) {
        List<VintedListingId> skipped = new ArrayList<>(skippedListings);
        skipped.add(listing);
        return new AutomaticRun(status, startedAt, endedAt, waitingUntil, currentListings, relaunchCount, skipped,
                stopReason, pauseRequested, stopRequested);
    }

    public AutomaticRun waitingUntil(Instant until) {
        return new AutomaticRun(RunStatus.WAITING, startedAt, endedAt, Optional.of(until), currentListings,
                relaunchCount, skippedListings, stopReason, pauseRequested, stopRequested);
    }

    /** Ignored unless the run is in progress and not paused already. */
    public AutomaticRun withPauseRequested() {
        if (!inProgress() || status == RunStatus.PAUSED) {
            return this;
        }
        return new AutomaticRun(status, startedAt, endedAt, waitingUntil, currentListings, relaunchCount,
                skippedListings, stopReason, true, stopRequested);
    }

    /** A resume asked for since the pause was seen cancels it: the run goes on. */
    public AutomaticRun pausedIfRequested() {
        if (!pauseRequested) {
            return this;
        }
        return new AutomaticRun(RunStatus.PAUSED, startedAt, endedAt, Optional.empty(), currentListings,
                relaunchCount, skippedListings, stopReason, false, stopRequested);
    }

    /** Ignored unless the run is paused, or a pause is asked for. */
    public AutomaticRun resumed() {
        if (status != RunStatus.PAUSED && !pauseRequested) {
            return this;
        }
        RunStatus resumed = status == RunStatus.PAUSED ? RunStatus.RUNNING : status;
        return new AutomaticRun(resumed, startedAt, endedAt, waitingUntil, currentListings, relaunchCount,
                skippedListings, stopReason, false, stopRequested);
    }

    /** Ignored unless the run is in progress. */
    public AutomaticRun withStopRequested() {
        if (!inProgress()) {
            return this;
        }
        return new AutomaticRun(status, startedAt, endedAt, waitingUntil, currentListings, relaunchCount,
                skippedListings, stopReason, pauseRequested, true);
    }

    public AutomaticRun finishedAt(Instant at) {
        return new AutomaticRun(RunStatus.FINISHED, startedAt, Optional.of(at), Optional.empty(), List.of(),
                relaunchCount, skippedListings, stopReason, false, false);
    }

    public AutomaticRun stoppedAt(Instant at, RunStopReason reason) {
        return new AutomaticRun(RunStatus.STOPPED, startedAt, Optional.of(at), Optional.empty(), List.of(),
                relaunchCount, skippedListings, Optional.of(reason), false, false);
    }
}
