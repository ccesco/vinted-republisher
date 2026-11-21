package fr.vinted.republisher.application.run;

import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.application.relaunch.RelaunchListing;
import fr.vinted.republisher.domain.cadence.CadenceLimitReached;
import fr.vinted.republisher.domain.cadence.WriteOrigin;
import fr.vinted.republisher.domain.inventory.InventoryCriteria;
import fr.vinted.republisher.domain.inventory.InventoryEntry;
import fr.vinted.republisher.domain.inventory.ListingStage;
import fr.vinted.republisher.domain.relaunch.FailureHandling;
import fr.vinted.republisher.domain.relaunch.RelaunchAction;
import fr.vinted.republisher.domain.relaunch.RelaunchReport;
import fr.vinted.republisher.domain.run.AutomaticRun;
import fr.vinted.republisher.domain.run.ParallelismMode;
import fr.vinted.republisher.domain.run.RunStatus;
import fr.vinted.republisher.domain.run.RunStopReason;
import fr.vinted.republisher.domain.run.RunWaiting;
import fr.vinted.republisher.domain.run.RunWaitingReason;
import fr.vinted.republisher.domain.run.RunWorkerKind;
import fr.vinted.republisher.domain.session.BrowserBusy;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.UnaryOperator;

/**
 * Relaunches every ready listing, as the member asked with one click, each by a relaunch with all its safeguards.
 * Waits when a failure says so, sets a listing aside when it is refused for itself or does not move on, and stops on a
 * problem of the account or of Vinted. Never starts on its own.
 * <p>
 * The run goes by workers (US-46), each taking the stages of its kind and going at its own pace, never two on the same
 * listing: one worker taking every stage in sequential mode (US-19), three in their own tabs with workers. A wait only
 * holds its own worker; a pause or a stop lets every worker finish its relaunch; the run ends once its workers have
 * nothing left to do.
 */
public class LaunchAutomaticRun {

    private static final Logger LOG = LoggerFactory.getLogger(LaunchAutomaticRun.class);

    /** How long to wait for a browser taken by another action before trying again. */
    private static final Duration BUSY_BROWSER_RETRY = Duration.ofSeconds(30);

    /** A paused run waits by steps, until it is resumed or stopped. */
    private static final Duration PAUSE_CHECK = Duration.ofHours(1);

    private final ShowListingInventory inventory;
    private final RelaunchListing relaunchListing;
    private final RunWaiting waiting;
    private final Clock clock;
    private final Executor background;
    private final ParallelismMode mode;
    private final Executor workerThreads;
    private AutomaticRun run = AutomaticRun.idle();
    private Crew crew = new Crew();

    /** {@code workerThreads} runs the workers, at least one thread each, when there are several. */
    public LaunchAutomaticRun(ShowListingInventory inventory, RelaunchListing relaunchListing, RunWaiting waiting,
                              Clock clock, Executor background, ParallelismMode mode, Executor workerThreads) {
        this.inventory = inventory;
        this.relaunchListing = relaunchListing;
        this.waiting = waiting;
        this.clock = clock;
        this.background = background;
        this.mode = mode;
        this.workerThreads = workerThreads;
    }

    public synchronized AutomaticRun overview() {
        return run;
    }

    /** Starts right away, so that the run shows as in progress, then goes on in the background; ignored while one runs. */
    public void launch() {
        Crew started = new Crew();
        synchronized (this) {
            if (run.inProgress()) {
                return;
            }
            run = AutomaticRun.startedAt(clock.instant(), mode.workerKinds());
            crew = started;
        }
        try {
            background.execute(() -> runToTheEnd(started));
        } catch (RejectedExecutionException rejected) {
            update(current -> current.stoppedAt(clock.instant(), RunStopReason.RUN_FAILED));
            throw rejected;
        }
    }

    /** A listing at a stage: relaunched at most once in a run. */
    private record Step(VintedListingId listing, ListingStage stage) {
    }

    /**
     * What the workers of one run share, under its own lock: the steps relaunched, the listings being relaunched, the
     * reason the run stops, whether it is over, and a count of the changes that may give an idle worker some work.
     * The run's overview is changed under this lock too, never the other way round.
     */
    private static final class Crew {

        private final Set<Step> relaunchedSteps = new HashSet<>();
        private final Set<VintedListingId> taken = new HashSet<>();
        /** Workers that found nothing to do since the last change: the run is over once they all did. */
        private final Set<RunWorkerKind> foundNothing = new HashSet<>();
        private Optional<RunStopReason> stopReason = Optional.empty();
        private boolean over;
        private long changes;

        private synchronized boolean ends() {
            return over || stopReason.isPresent();
        }

        private synchronized void stopOn(RunStopReason reason) {
            stopReason = stopReason.or(() -> Optional.of(reason));
            changed();
        }

        /** Every idle worker looks again: the change may give it something to do. */
        private synchronized void changed() {
            changes++;
            foundNothing.clear();
            notifyAll();
        }
    }

    private void runToTheEnd(Crew workers) {
        List<RunWorkerKind> kinds = mode.workerKinds();
        if (kinds.size() == 1) {
            work(kinds.getFirst(), workers);
        } else {
            startTogether(kinds, workers);
        }
        // The run ends on its workers' decisions, never on a status read again: a new run may start once it ended.
        Optional<RunStopReason> stopReason;
        synchronized (workers) {
            stopReason = workers.stopReason;
        }
        if (stopReason.isPresent()) {
            update(current -> current.stoppedAt(clock.instant(), stopReason.get()));
        } else if (overview().stopRequested()) {
            update(current -> current.stoppedAt(clock.instant(), RunStopReason.STOPPED_BY_MEMBER));
        } else {
            update(current -> current.finishedAt(clock.instant()));
        }
    }

    /**
     * Each worker on its own thread, until all of them are over. A worker that cannot start stops the others, which
     * finish their relaunch: the run then ends as failed instead of staying in progress.
     */
    private void startTogether(List<RunWorkerKind> kinds, Crew workers) {
        List<CompletableFuture<Void>> started = new ArrayList<>();
        try {
            for (RunWorkerKind kind : kinds) {
                started.add(CompletableFuture.runAsync(() -> work(kind, workers), workerThreads));
            }
        } catch (RejectedExecutionException rejected) {
            LOG.error("Automatic run workers could not start", rejected);
            workers.stopOn(RunStopReason.RUN_FAILED);
        }
        CompletableFuture.allOf(started.toArray(CompletableFuture[]::new)).join();
    }

    /** One worker, until the run stops or no worker has anything left to do. */
    private void work(RunWorkerKind kind, Crew workers) {
        try {
            while (!workers.ends() && !overview().stopRequested()) {
                if (overview().holdsWorkers()) {
                    holdWhilePaused(kind, workers);
                } else {
                    takeNext(kind, workers);
                }
            }
        } catch (RuntimeException unexpected) {
            LOG.error("Automatic run worker {} failed", kind, unexpected);
            workers.stopOn(RunStopReason.RUN_FAILED);
        }
    }

    /** What a worker chose: a listing to relaunch, a date to wait for, or nothing to do since a count of changes. */
    private sealed interface Choice {
    }

    private record Take(InventoryEntry entry) implements Choice {
    }

    private record WaitFor(Instant until, RunWaitingReason reason) implements Choice {
    }

    private record NothingSince(long changes) implements Choice {
    }

    private void takeNext(RunWorkerKind kind, Crew workers) {
        switch (choose(kind, workers)) {
            case Take take -> relaunch(kind, take.entry(), workers);
            case WaitFor wait -> waitUntil(kind, wait.until(), wait.reason(), workers);
            case NothingSince nothing -> idleUntilAChange(nothing.changes(), workers);
        }
    }

    /**
     * The first listing at the worker's stages, in its order then by Vinted identifier, leaving out the listings set
     * aside and those another worker relaunches; any back at a stage already relaunched is set aside. A real write the
     * cadence does not allow yet is waited for, never tried only to be refused.
     */
    private Choice choose(RunWorkerKind kind, Crew workers) {
        synchronized (workers) {
            List<ListingStage> stages = kind.stages();
            List<InventoryEntry> candidates = inventory.show(InventoryCriteria.everyListing()).stream()
                    .filter(entry -> stages.contains(entry.stage()))
                    .filter(entry -> !overview().skips(entry.listing().id()))
                    .filter(entry -> !workers.taken.contains(entry.listing().id()))
                    .sorted(Comparator.comparingInt((InventoryEntry entry) -> stages.indexOf(entry.stage()))
                            .thenComparingLong(entry -> entry.listing().id().value()))
                    .toList();
            for (InventoryEntry entry : candidates) {
                VintedListingId id = entry.listing().id();
                // Back at a stage already relaunched (a deletion found not done, ready again): never write twice.
                if (workers.relaunchedSteps.contains(new Step(id, entry.stage()))) {
                    update(current -> current.skipping(id));
                    continue;
                }
                Optional<Instant> writeAllowedAt = relaunchListing.realWriteAllowedAt(entry.stage());
                if (writeAllowedAt.filter(clock.instant()::isBefore).isPresent()) {
                    workers.foundNothing.remove(kind);
                    return new WaitFor(writeAllowedAt.get(), RunWaitingReason.CADENCE);
                }
                workers.foundNothing.remove(kind);
                workers.taken.add(id);
                RelaunchAction action = RelaunchAction.after(entry.stage(), WriteOrigin.RUN)
                        .orElse(RelaunchAction.REPUBLICATION);
                update(current -> current.withWorker(current.worker(kind).working(id, action)));
                return new Take(entry);
            }
            update(current -> current.withWorker(current.worker(kind).idle()));
            workers.foundNothing.add(kind);
            long seen = workers.changes;
            if (workers.foundNothing.containsAll(mode.workerKinds())) {
                workers.over = true;
                workers.changed();
            }
            return new NothingSince(seen);
        }
    }

    /** Relaunches the listing, then applies what it came to (US-17). */
    private void relaunch(RunWorkerKind kind, InventoryEntry entry, Crew workers) {
        VintedListingId id = entry.listing().id();
        Optional<RelaunchReport> report = Optional.empty();
        Optional<RuntimeException> failure = Optional.empty();
        try {
            report = Optional.of(relaunchListing.relaunch(id, entry.stage(), WriteOrigin.RUN));
        } catch (RuntimeException refused) {
            failure = Optional.of(refused);
        }
        Optional<WaitFor> wait = Optional.empty();
        synchronized (workers) {
            workers.taken.remove(id);
            if (report.isPresent()) {
                workers.relaunchedSteps.add(new Step(id, entry.stage()));
                // A listing that does not move on (dry run, outcome still to check) waits for the member.
                boolean stays = report.get().entry().stage() == entry.stage();
                update(current -> stays ? current.relaunchedBy(kind).skipping(id) : current.relaunchedBy(kind));
            } else {
                wait = applyFailure(id, failure.orElseThrow(), workers);
            }
            workers.changed();
        }
        wait.ifPresent(until -> waitUntil(kind, until.until(), until.reason(), workers));
    }

    private Optional<WaitFor> applyFailure(VintedListingId id, RuntimeException failure, Crew workers) {
        FailureHandling handling = FailureHandling.of(failure);
        switch (handling.conduct()) {
            case WAIT -> {
                Instant until = handling.until().orElseGet(() -> clock.instant().plus(BUSY_BROWSER_RETRY));
                return Optional.of(new WaitFor(until, waitingReason(failure)));
            }
            case SKIP_LISTING -> update(current -> current.skipping(id));
            case STOP_RUN -> {
                LOG.warn("Automatic run stopping on listing {}", id.value(), failure);
                workers.stopOn(RunStopReason.of(failure));
            }
        }
        return Optional.empty();
    }

    private static RunWaitingReason waitingReason(RuntimeException failure) {
        return switch (failure) {
            case CadenceLimitReached _ -> RunWaitingReason.CADENCE;
            case BrowserBusy _ -> RunWaitingReason.BROWSER_BUSY;
            default -> RunWaitingReason.REPUBLICATION_DELAY;
        };
    }

    /** Only this worker waits; a pause, a stop or the end of the run cuts the wait short. */
    private void waitUntil(RunWorkerKind kind, Instant until, RunWaitingReason reason, Crew workers) {
        synchronized (workers) {
            update(current -> current.withWorker(current.worker(kind).waiting(until, reason)));
        }
        waiting.until(until, () -> pauseOrStopRequested() || workers.ends());
    }

    /** Until another worker ends an action, the member pauses, resumes or stops, or the run is over. */
    private void idleUntilAChange(long seen, Crew workers) {
        synchronized (workers) {
            while (workers.changes == seen && !workers.ends()) {
                try {
                    workers.wait();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    workers.stopOn(RunStopReason.RUN_FAILED);
                }
            }
        }
    }

    /**
     * The worker steps out of the run; the run is paused once no worker relaunches any more. Until then the worker
     * waits for the others; once paused, it waits by steps until the member resumes or stops.
     */
    private void holdWhilePaused(RunWorkerKind kind, Crew workers) {
        long seen;
        synchronized (workers) {
            boolean pausedAlready = overview().status() == RunStatus.PAUSED;
            update(current -> current.withWorker(current.worker(kind).stopped()).pausedIfRequested());
            // Only the worker that pauses the run tells the others, which would otherwise wake each other in turn.
            if (!pausedAlready && overview().status() == RunStatus.PAUSED) {
                workers.changed();
            }
            seen = workers.changes;
        }
        if (overview().status() == RunStatus.PAUSED) {
            waiting.until(clock.instant().plus(PAUSE_CHECK), this::resumedOrStopRequested);
        } else {
            idleUntilAChange(seen, workers);
        }
    }

    private boolean pauseOrStopRequested() {
        AutomaticRun current = overview();
        return current.pauseRequested() || current.stopRequested();
    }

    /** A pause asked for again right after a resume ends the wait too: the worker then pauses the run at once. */
    private boolean resumedOrStopRequested() {
        AutomaticRun current = overview();
        return current.status() != RunStatus.PAUSED || current.stopRequested();
    }

    /** Ignored unless a run is in progress and not paused: takes effect once no worker relaunches any more. */
    public void pause() {
        update(AutomaticRun::withPauseRequested);
        currentCrew().changed();
    }

    /** Ignored unless the run is paused or a pause is asked for. */
    public void resume() {
        update(AutomaticRun::resumed);
        currentCrew().changed();
    }

    /** Ignored unless a run is in progress: takes effect once every worker has finished its relaunch. */
    public void stop() {
        update(AutomaticRun::withStopRequested);
        currentCrew().changed();
    }

    private synchronized Crew currentCrew() {
        return crew;
    }

    private synchronized void update(UnaryOperator<AutomaticRun> change) {
        run = change.apply(run);
    }
}
