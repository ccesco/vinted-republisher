package fr.vinted.republisher.domain.run;

import fr.vinted.republisher.domain.relaunch.RelaunchAction;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A worker of the run as the member follows it (US-46): its kind, what it does now (the listing and action it
 * relaunches, or the date and reason it waits for) and the relaunches it made.
 */
public record RunWorker(RunWorkerKind kind, RunWorkerState state, Optional<VintedListingId> listing,
                        Optional<RelaunchAction> action, Optional<Instant> waitingUntil,
                        Optional<RunWaitingReason> waitingReason, int relaunchCount) {

    public RunWorker {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(state);
        Objects.requireNonNull(listing);
        Objects.requireNonNull(action);
        Objects.requireNonNull(waitingUntil);
        Objects.requireNonNull(waitingReason);
    }

    public static RunWorker idle(RunWorkerKind kind) {
        return new RunWorker(kind, RunWorkerState.IDLE, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), 0);
    }

    public RunWorker working(VintedListingId on, RelaunchAction doing) {
        return new RunWorker(kind, RunWorkerState.WORKING, Optional.of(on), Optional.of(doing), Optional.empty(),
                Optional.empty(), relaunchCount);
    }

    public RunWorker waiting(Instant until, RunWaitingReason reason) {
        return new RunWorker(kind, RunWorkerState.WAITING, Optional.empty(), Optional.empty(), Optional.of(until),
                Optional.of(reason), relaunchCount);
    }

    public RunWorker idle() {
        return new RunWorker(kind, RunWorkerState.IDLE, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), relaunchCount);
    }

    public RunWorker stopped() {
        return new RunWorker(kind, RunWorkerState.STOPPED, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), relaunchCount);
    }

    public RunWorker relaunched() {
        return new RunWorker(kind, state, listing, action, waitingUntil, waitingReason, relaunchCount + 1);
    }

    public boolean worksOn(VintedListingId other) {
        return state == RunWorkerState.WORKING && listing.equals(Optional.of(other));
    }
}
