package fr.vinted.republisher.domain.run;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The automatic run as the member follows it: its status and dates, what each of its workers does (US-46), the
 * relaunches done, the listings set aside for the member to decide, and the pause or stop the member asked for, which
 * takes effect once no worker is relaunching. While it runs, its status follows its workers: running while one
 * relaunches, waiting until the nearest date while none does and one waits. Kept in memory: the actions are in the
 * journal.
 */
public record AutomaticRun(RunStatus status, Optional<Instant> startedAt, Optional<Instant> endedAt,
                           Optional<Instant> waitingUntil, List<RunWorker> workers,
                           int relaunchCount, List<VintedListingId> skippedListings,
                           Optional<RunStopReason> stopReason, boolean pauseRequested, boolean stopRequested) {

    public AutomaticRun {
        Objects.requireNonNull(status);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(endedAt);
        Objects.requireNonNull(waitingUntil);
        workers = List.copyOf(workers);
        skippedListings = List.copyOf(skippedListings);
        Objects.requireNonNull(stopReason);
    }

    public static AutomaticRun idle() {
        return new AutomaticRun(RunStatus.IDLE, Optional.empty(), Optional.empty(), Optional.empty(),
                List.of(), 0, List.of(), Optional.empty(), false, false);
    }

    public static AutomaticRun startedAt(Instant at, List<RunWorkerKind> kinds) {
        return new AutomaticRun(RunStatus.RUNNING, Optional.of(at), Optional.empty(), Optional.empty(),
                kinds.stream().map(RunWorker::idle).toList(), 0, List.of(), Optional.empty(), false, false);
    }

    /** Running, waiting or paused: a new launch is ignored, a pause or a stop can be asked for. */
    public boolean inProgress() {
        return status == RunStatus.RUNNING || status == RunStatus.WAITING || status == RunStatus.PAUSED;
    }

    public boolean skips(VintedListingId listing) {
        return skippedListings.contains(listing);
    }

    /**
     * Whether the run will not take the listing again: set aside by a run in progress, or being relaunched by one of
     * its workers (US-41).
     */
    public boolean leavesOut(VintedListingId listing) {
        return inProgress() && skips(listing) || workers.stream().anyMatch(worker -> worker.worksOn(listing));
    }

    public RunWorker worker(RunWorkerKind kind) {
        return workers.stream().filter(worker -> worker.kind() == kind).findFirst().orElseThrow();
    }

    /** The worker replaces the one of its kind; a run running or waiting then follows its workers. */
    public AutomaticRun withWorker(RunWorker worker) {
        List<RunWorker> changed = workers.stream()
                .map(current -> current.kind() == worker.kind() ? worker : current).toList();
        if (status != RunStatus.RUNNING && status != RunStatus.WAITING) {
            return new AutomaticRun(status, startedAt, endedAt, waitingUntil, changed, relaunchCount,
                    skippedListings, stopReason, pauseRequested, stopRequested);
        }
        boolean working = changed.stream().anyMatch(current -> current.state() == RunWorkerState.WORKING);
        Optional<Instant> nearestWait = working ? Optional.empty() : changed.stream()
                .map(RunWorker::waitingUntil).flatMap(Optional::stream).min(Instant::compareTo);
        RunStatus following = nearestWait.isPresent() ? RunStatus.WAITING : RunStatus.RUNNING;
        return new AutomaticRun(following, startedAt, endedAt, nearestWait, changed, relaunchCount,
                skippedListings, stopReason, pauseRequested, stopRequested);
    }

    public AutomaticRun relaunchedBy(RunWorkerKind kind) {
        List<RunWorker> changed = workers.stream()
                .map(current -> current.kind() == kind ? current.relaunched() : current).toList();
        return new AutomaticRun(status, startedAt, endedAt, waitingUntil, changed, relaunchCount + 1,
                skippedListings, stopReason, pauseRequested, stopRequested);
    }

    public AutomaticRun skipping(VintedListingId listing) {
        List<VintedListingId> skipped = new ArrayList<>(skippedListings);
        skipped.add(listing);
        return new AutomaticRun(status, startedAt, endedAt, waitingUntil, workers, relaunchCount, skipped,
                stopReason, pauseRequested, stopRequested);
    }

    /** Ignored unless the run is in progress and not paused already. */
    public AutomaticRun withPauseRequested() {
        if (!inProgress() || status == RunStatus.PAUSED) {
            return this;
        }
        return new AutomaticRun(status, startedAt, endedAt, waitingUntil, workers, relaunchCount,
                skippedListings, stopReason, true, stopRequested);
    }

    /** Paused once asked for and no worker relaunches any more; a resume asked for meanwhile cancels it. */
    public AutomaticRun pausedIfRequested() {
        boolean working = workers.stream().anyMatch(worker -> worker.state() == RunWorkerState.WORKING);
        if (!pauseRequested || working) {
            return this;
        }
        return new AutomaticRun(RunStatus.PAUSED, startedAt, endedAt, Optional.empty(), workers,
                relaunchCount, skippedListings, stopReason, false, stopRequested);
    }

    /** Whether a pause, asked for or effective, holds the workers. */
    public boolean holdsWorkers() {
        return pauseRequested || status == RunStatus.PAUSED;
    }

    /** Ignored unless the run is paused, or a pause is asked for. */
    public AutomaticRun resumed() {
        if (status != RunStatus.PAUSED && !pauseRequested) {
            return this;
        }
        RunStatus resumed = status == RunStatus.PAUSED ? RunStatus.RUNNING : status;
        return new AutomaticRun(resumed, startedAt, endedAt, waitingUntil, workers, relaunchCount,
                skippedListings, stopReason, false, stopRequested);
    }

    /** Ignored unless the run is in progress. */
    public AutomaticRun withStopRequested() {
        if (!inProgress()) {
            return this;
        }
        return new AutomaticRun(status, startedAt, endedAt, waitingUntil, workers, relaunchCount,
                skippedListings, stopReason, pauseRequested, true);
    }

    public AutomaticRun finishedAt(Instant at) {
        return new AutomaticRun(RunStatus.FINISHED, startedAt, Optional.of(at), Optional.empty(), stoppedWorkers(),
                relaunchCount, skippedListings, stopReason, false, false);
    }

    public AutomaticRun stoppedAt(Instant at, RunStopReason reason) {
        return new AutomaticRun(RunStatus.STOPPED, startedAt, Optional.of(at), Optional.empty(), stoppedWorkers(),
                relaunchCount, skippedListings, Optional.of(reason), false, false);
    }

    private List<RunWorker> stoppedWorkers() {
        return workers.stream().map(RunWorker::stopped).toList();
    }
}
