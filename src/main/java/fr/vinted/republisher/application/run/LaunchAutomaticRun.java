package fr.vinted.republisher.application.run;

import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.application.relaunch.RelaunchListing;
import fr.vinted.republisher.domain.inventory.BackupFilter;
import fr.vinted.republisher.domain.inventory.InventoryCriteria;
import fr.vinted.republisher.domain.inventory.InventoryEntry;
import fr.vinted.republisher.domain.inventory.InventorySort;
import fr.vinted.republisher.domain.inventory.ListingStage;
import fr.vinted.republisher.domain.inventory.PresenceFilter;
import fr.vinted.republisher.domain.inventory.SortDirection;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.UnaryOperator;

/**
 * Relaunches every ready listing in turn, as the member asked with one click: checks first, then republications,
 * deletions, and waits, each by a relaunch with all its safeguards. Waits when a failure says so, sets a listing aside
 * when it is refused for itself or does not move on, and stops on a problem of the account or of Vinted. Never
 * starts on its own.
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

    private static final InventoryCriteria EVERY_LISTING = new InventoryCriteria(PresenceFilter.ALL,
            BackupFilter.ALL, "", InventorySort.RECENCY, SortDirection.DESC);

    private final ShowListingInventory inventory;
    private final RelaunchListing relaunchListing;
    private final RunWaiting waiting;
    private final Clock clock;
    private final Executor background;
    private final Set<Step> relaunchedSteps = new HashSet<>();
    private AutomaticRun run = AutomaticRun.idle();

    public LaunchAutomaticRun(ShowListingInventory inventory, RelaunchListing relaunchListing, RunWaiting waiting,
                              Clock clock, Executor background) {
        this.inventory = inventory;
        this.relaunchListing = relaunchListing;
        this.waiting = waiting;
        this.clock = clock;
        this.background = background;
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
            // A pause or a stop takes effect between two relaunches, never during one. The loop leaves on its own
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
                    Optional<InventoryEntry> next = nextListing();
                    if (next.isEmpty()) {
                        update(run -> run.finishedAt(clock.instant()));
                        goesOn = false;
                    } else {
                        goesOn = relaunch(next.get());
                    }
                }
            }
        } catch (RuntimeException unexpected) {
            // The run must end whatever happens; the failure is rethrown for the executor to log it.
            update(current -> current.stoppedAt(clock.instant(), RunStopReason.RUN_FAILED));
            throw unexpected;
        }
    }

    private Optional<InventoryEntry> nextListing() {
        AutomaticRun current = overview();
        return inventory.show(EVERY_LISTING).stream()
                .filter(entry -> ORDER.contains(entry.stage()))
                .filter(entry -> !current.skips(entry.listing().id()))
                .min(Comparator.comparingInt((InventoryEntry entry) -> ORDER.indexOf(entry.stage()))
                        .thenComparingLong(entry -> entry.listing().id().value()));
    }

    /** Whether the run goes on after this listing. */
    private boolean relaunch(InventoryEntry entry) {
        VintedListingId id = entry.listing().id();
        Step step = new Step(id, entry.stage());
        // Back at a stage already relaunched (a deletion found not done, ready again): never write twice in a run.
        if (relaunchedSteps.contains(step)) {
            update(current -> current.skipping(id));
            return true;
        }
        update(current -> current.relaunching(id));
        try {
            RelaunchReport report = relaunchListing.relaunch(id, entry.stage());
            relaunchedSteps.add(step);
            // A listing that does not move on (dry run, outcome still to check) waits for the member.
            boolean stays = report.entry().stage() == entry.stage();
            update(current -> stays ? current.relaunched().skipping(id) : current.relaunched());
            return true;
        } catch (RuntimeException failure) {
            return handle(id, failure);
        }
    }

    /** Whether the run goes on after this failure. */
    private boolean handle(VintedListingId id, RuntimeException failure) {
        FailureHandling handling = FailureHandling.of(failure);
        switch (handling.conduct()) {
            case WAIT -> waitUntil(handling.until().orElseGet(() -> clock.instant().plus(BUSY_BROWSER_RETRY)));
            case SKIP_LISTING -> update(current -> current.skipping(id));
            case STOP_RUN -> {
                LOG.warn("Automatic run stopped on listing {}", id.value(), failure);
                update(current -> current.stoppedAt(clock.instant(), RunStopReason.of(failure)));
                return false;
            }
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

    /** Ignored unless a run is in progress and not paused: takes effect once the current relaunch is over. */
    public void pause() {
        update(AutomaticRun::withPauseRequested);
    }

    /** Ignored unless the run is paused or a pause is asked for. */
    public void resume() {
        update(AutomaticRun::resumed);
    }

    /** Ignored unless a run is in progress: takes effect once the current relaunch is over. */
    public void stop() {
        update(AutomaticRun::withStopRequested);
    }

    private synchronized void update(UnaryOperator<AutomaticRun> change) {
        run = change.apply(run);
    }
}
