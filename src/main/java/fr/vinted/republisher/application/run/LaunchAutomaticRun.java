package fr.vinted.republisher.application.run;

import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.application.relaunch.RelaunchListing;
import fr.vinted.republisher.domain.inventory.InventoryCriteria;
import fr.vinted.republisher.domain.inventory.InventoryEntry;
import fr.vinted.republisher.domain.inventory.ListingStage;
import fr.vinted.republisher.domain.relaunch.FailureHandling;
import fr.vinted.republisher.domain.relaunch.RelaunchReport;
import fr.vinted.republisher.domain.run.AutomaticRun;
import fr.vinted.republisher.domain.run.RunStatus;
import fr.vinted.republisher.domain.run.RunStopReason;
import fr.vinted.republisher.domain.run.RunWaiting;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.UnaryOperator;

/**
 * Relaunches every ready listing in turn, as the member asked with one click: checks first, then republications,
 * deletions, and waits, each by a relaunch with all its safeguards. Waits when a failure says so, sets a listing aside
 * when it is refused for itself or does not move on, and stops on a problem of the account or of Vinted. Never
 * starts on its own.
 * <p>
 * Relaunches go by rounds (US-15): up to {@code parallelism} listings relaunched together, each in its own tab, then
 * their outcomes applied in the round's order. Pauses, stops and waits are decided between two rounds, by the run's
 * thread alone; with a parallelism of 1, a round is one relaunch.
 */
public class LaunchAutomaticRun {

    private static final Logger LOG = LoggerFactory.getLogger(LaunchAutomaticRun.class);

    /** How long to wait for a browser taken by another action before trying again. */
    private static final Duration BUSY_BROWSER_RETRY = Duration.ofSeconds(30);

    /** A paused run waits by steps, until it is resumed or stopped. */
    private static final Duration PAUSE_CHECK = Duration.ofHours(1);

    /** Stages the run relaunches, in the order it takes them. */
    private static final List<ListingStage> ORDER = List.of(ListingStage.REPUBLICATION_TO_CHECK,
            ListingStage.DELETION_TO_CHECK, ListingStage.TO_REPUBLISH, ListingStage.TO_DELETE, ListingStage.WAITING);

    private final ShowListingInventory inventory;
    private final RelaunchListing relaunchListing;
    private final RunWaiting waiting;
    private final Clock clock;
    private final Executor background;
    private final int parallelism;
    private final Executor relaunchers;
    private final Set<Step> relaunchedSteps = new HashSet<>();
    private AutomaticRun run = AutomaticRun.idle();

    /** {@code relaunchers} runs the relaunches of a round, at least {@code parallelism} at once. */
    public LaunchAutomaticRun(ShowListingInventory inventory, RelaunchListing relaunchListing, RunWaiting waiting,
                              Clock clock, Executor background, int parallelism, Executor relaunchers) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("at least one relaunch at a time: " + parallelism);
        }
        this.inventory = inventory;
        this.relaunchListing = relaunchListing;
        this.waiting = waiting;
        this.clock = clock;
        this.background = background;
        this.parallelism = parallelism;
        this.relaunchers = relaunchers;
    }

    public synchronized AutomaticRun overview() {
        return run;
    }

    /** Starts right away, so that the run shows as in progress, then goes on in the background; ignored while one runs. */
    public void launch() {
        synchronized (this) {
            if (run.inProgress()) {
                return;
            }
            run = AutomaticRun.startedAt(clock.instant());
        }
        try {
            background.execute(this::runToTheEnd);
        } catch (RejectedExecutionException rejected) {
            update(current -> current.stoppedAt(clock.instant(), RunStopReason.RUN_FAILED));
            throw rejected;
        }
    }

    /** A listing at a stage: relaunched at most once in a run. Only the run's thread uses them. */
    private record Step(VintedListingId listing, ListingStage stage) {
    }

    private void runToTheEnd() {
        relaunchedSteps.clear();
        try {
            // A pause or a stop takes effect between two rounds, never during one. The loop leaves on its own
            // decisions, never on a status read again: a new run may have started once this one ended.
            boolean goesOn = true;
            while (goesOn) {
                AutomaticRun current = overview();
                if (current.stopRequested()) {
                    update(run -> run.stoppedAt(clock.instant(), RunStopReason.STOPPED_BY_MEMBER));
                    goesOn = false;
                } else if (current.pauseRequested()) {
                    pauseUntilResumedOrStopped();
                } else {
                    List<InventoryEntry> round = nextRound();
                    if (round.isEmpty()) {
                        update(run -> run.finishedAt(clock.instant()));
                        goesOn = false;
                    } else {
                        goesOn = relaunchTogether(round);
                    }
                }
            }
        } catch (RuntimeException unexpected) {
            // The run must end whatever happens; the failure is rethrown for the executor to log it.
            update(current -> current.stoppedAt(clock.instant(), RunStopReason.RUN_FAILED));
            throw unexpected;
        }
    }

    /** Up to {@code parallelism} listings, in the run's order; any back at a stage already relaunched is skipped. */
    private List<InventoryEntry> nextRound() {
        AutomaticRun current = overview();
        List<InventoryEntry> candidates = inventory.show(InventoryCriteria.everyListing()).stream()
                .filter(entry -> ORDER.contains(entry.stage()))
                .filter(entry -> !current.skips(entry.listing().id()))
                .sorted(Comparator.comparingInt((InventoryEntry entry) -> ORDER.indexOf(entry.stage()))
                        .thenComparingLong(entry -> entry.listing().id().value()))
                .toList();
        List<InventoryEntry> round = new ArrayList<>();
        // Only listings reached before the round is full are looked at: the others may still move on meanwhile.
        for (int next = 0; next < candidates.size() && round.size() < parallelism; next++) {
            InventoryEntry entry = candidates.get(next);
            VintedListingId id = entry.listing().id();
            // Back at a stage already relaunched (a deletion found not done, ready again): never write twice in a run.
            if (relaunchedSteps.contains(new Step(id, entry.stage()))) {
                update(run -> run.skipping(id));
            } else {
                round.add(entry);
            }
        }
        return round;
    }

    /** What a relaunch of the round came to: its report, or the failure that stopped it. */
    private record Outcome(InventoryEntry entry, Optional<RelaunchReport> report, Optional<RuntimeException> failure) {

        private VintedListingId id() {
            return entry.listing().id();
        }
    }

    /**
     * Relaunches the round's listings together and waits for every one of them: none is ever cut. Whether the run goes
     * on after the round.
     */
    private boolean relaunchTogether(List<InventoryEntry> round) {
        update(current -> current.relaunching(round.stream().map(entry -> entry.listing().id()).toList()));
        List<CompletableFuture<Outcome>> relaunches = round.stream()
                .map(entry -> CompletableFuture.supplyAsync(() -> relaunch(entry), relaunchers))
                .toList();
        return apply(relaunches.stream().map(CompletableFuture::join).toList());
    }

    private Outcome relaunch(InventoryEntry entry) {
        try {
            RelaunchReport report = relaunchListing.relaunch(entry.listing().id(), entry.stage());
            return new Outcome(entry, Optional.of(report), Optional.empty());
        } catch (RuntimeException failure) {
            return new Outcome(entry, Optional.empty(), Optional.of(failure));
        }
    }

    /**
     * Applies the outcomes in the round's order. The run stops on the first problem of the account or of Vinted; it
     * waits only when every relaunch of the round asks to, until the nearest date; otherwise the next round starts.
     */
    private boolean apply(List<Outcome> outcomes) {
        boolean movedOn = false;
        Optional<Instant> nearestWait = Optional.empty();
        Optional<RunStopReason> stop = Optional.empty();
        for (Outcome outcome : outcomes) {
            VintedListingId id = outcome.id();
            if (outcome.report().isPresent()) {
                relaunchedSteps.add(new Step(id, outcome.entry().stage()));
                // A listing that does not move on (dry run, outcome still to check) waits for the member.
                boolean stays = outcome.report().get().entry().stage() == outcome.entry().stage();
                update(current -> stays ? current.relaunched().skipping(id) : current.relaunched());
                movedOn = true;
                continue;
            }
            RuntimeException failure = outcome.failure().orElseThrow();
            FailureHandling handling = FailureHandling.of(failure);
            switch (handling.conduct()) {
                case WAIT -> {
                    Instant until = handling.until().orElseGet(() -> clock.instant().plus(BUSY_BROWSER_RETRY));
                    nearestWait = nearestWait.filter(earlier -> earlier.isBefore(until)).or(() -> Optional.of(until));
                }
                case SKIP_LISTING -> {
                    update(current -> current.skipping(id));
                    movedOn = true;
                }
                case STOP_RUN -> {
                    LOG.warn("Automatic run stopping on listing {}", id.value(), failure);
                    stop = stop.or(() -> Optional.of(RunStopReason.of(failure)));
                }
            }
        }
        if (stop.isPresent()) {
            RunStopReason reason = stop.get();
            update(current -> current.stoppedAt(clock.instant(), reason));
            return false;
        }
        if (!movedOn) {
            nearestWait.ifPresent(this::waitUntil);
        }
        return true;
    }

    private void waitUntil(Instant until) {
        update(current -> current.waitingUntil(until));
        waiting.until(until, this::pauseOrStopRequested);
    }

    private void pauseUntilResumedOrStopped() {
        update(AutomaticRun::pausedIfRequested);
        while (overview().status() == RunStatus.PAUSED && !overview().stopRequested()) {
            waiting.until(clock.instant().plus(PAUSE_CHECK), this::resumedOrStopRequested);
        }
    }

    private boolean pauseOrStopRequested() {
        AutomaticRun current = overview();
        return current.pauseRequested() || current.stopRequested();
    }

    private boolean resumedOrStopRequested() {
        AutomaticRun current = overview();
        return current.status() != RunStatus.PAUSED || current.stopRequested();
    }

    /** Ignored unless a run is in progress and not paused: takes effect once the current round is over. */
    public void pause() {
        update(AutomaticRun::withPauseRequested);
    }

    /** Ignored unless the run is paused or a pause is asked for. */
    public void resume() {
        update(AutomaticRun::resumed);
    }

    /** Ignored unless a run is in progress: takes effect once the current round is over. */
    public void stop() {
        update(AutomaticRun::withStopRequested);
    }

    private synchronized void update(UnaryOperator<AutomaticRun> change) {
        run = change.apply(run);
    }
}
