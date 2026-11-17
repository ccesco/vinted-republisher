package fr.vinted.republisher.domain.run;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The automatic run as the member follows it: its status and dates, the listing being relaunched, the relaunches done
 * and the listings set aside for the member to decide. Kept in memory: the actions themselves are in the journal.
 */
public record AutomaticRun(RunStatus status, Optional<Instant> startedAt, Optional<Instant> endedAt,
                           Optional<Instant> waitingUntil, Optional<VintedListingId> currentListing,
                           int relaunchCount, List<VintedListingId> skippedListings,
                           Optional<RunStopReason> stopReason) {

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
                Optional.empty(), 0, List.of(), Optional.empty());
    }

    public static AutomaticRun startedAt(Instant at) {
        return new AutomaticRun(RunStatus.RUNNING, Optional.of(at), Optional.empty(), Optional.empty(),
                Optional.empty(), 0, List.of(), Optional.empty());
    }

    public boolean inProgress() {
        return status == RunStatus.RUNNING || status == RunStatus.WAITING;
    }

    public boolean skips(VintedListingId listing) {
        return skippedListings.contains(listing);
    }

    public AutomaticRun relaunching(VintedListingId listing) {
        return new AutomaticRun(RunStatus.RUNNING, startedAt, endedAt, Optional.empty(), Optional.of(listing),
                relaunchCount, skippedListings, stopReason);
    }

    public AutomaticRun relaunched() {
        return new AutomaticRun(status, startedAt, endedAt, waitingUntil, currentListing, relaunchCount + 1,
                skippedListings, stopReason);
    }

    public AutomaticRun skipping(VintedListingId listing) {
        List<VintedListingId> skipped = new ArrayList<>(skippedListings);
        skipped.add(listing);
        return new AutomaticRun(status, startedAt, endedAt, waitingUntil, currentListing, relaunchCount, skipped,
                stopReason);
    }

    public AutomaticRun waitingUntil(Instant until) {
        return new AutomaticRun(RunStatus.WAITING, startedAt, endedAt, Optional.of(until), currentListing,
                relaunchCount, skippedListings, stopReason);
    }

    public AutomaticRun finishedAt(Instant at) {
        return new AutomaticRun(RunStatus.FINISHED, startedAt, Optional.of(at), Optional.empty(), Optional.empty(),
                relaunchCount, skippedListings, stopReason);
    }

    public AutomaticRun stoppedAt(Instant at, RunStopReason reason) {
        return new AutomaticRun(RunStatus.STOPPED, startedAt, Optional.of(at), Optional.empty(), Optional.empty(),
                relaunchCount, skippedListings, Optional.of(reason));
    }
}
