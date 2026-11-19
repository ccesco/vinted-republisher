package fr.vinted.republisher.domain.run;

import fr.vinted.republisher.domain.relaunch.RelaunchAction;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** US-46: the run's status follows its workers. */
class AutomaticRunTest {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
    private static final VintedListingId BONNET = new VintedListingId(1);
    private static final List<RunWorkerKind> WORKERS = ParallelismMode.WORKERS.workerKinds();

    private final AutomaticRun started = AutomaticRun.startedAt(NOW, WORKERS);

    private static RunWorker working(RunWorkerKind kind) {
        return RunWorker.idle(kind).working(BONNET, RelaunchAction.REPUBLICATION);
    }

    private static RunWorker waiting(RunWorkerKind kind, Instant until) {
        return RunWorker.idle(kind).waiting(until, RunWaitingReason.CADENCE);
    }

    @Test
    void a_run_starts_with_its_workers_idle_and_none_before() {
        assertThat(AutomaticRun.idle().workers()).isEmpty();
        assertThat(started.workers()).extracting(RunWorker::kind).containsExactlyElementsOf(WORKERS);
        assertThat(started.workers()).extracting(RunWorker::state).containsOnly(RunWorkerState.IDLE);
    }

    @Test
    void running_while_a_worker_is_working_even_when_another_waits() {
        AutomaticRun run = started.withWorker(waiting(RunWorkerKind.DELETION, NOW.plusSeconds(60)))
                .withWorker(working(RunWorkerKind.REPUBLICATION));

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(run.waitingUntil()).isEmpty();
    }

    @Test
    void waiting_until_the_nearest_date_when_no_worker_is_working() {
        AutomaticRun run = started.withWorker(waiting(RunWorkerKind.DELETION, NOW.plusSeconds(120)))
                .withWorker(waiting(RunWorkerKind.REPUBLICATION, NOW.plusSeconds(60)));

        assertThat(run.status()).isEqualTo(RunStatus.WAITING);
        assertThat(run.waitingUntil()).contains(NOW.plusSeconds(60));
    }

    @Test
    void a_worker_replaces_the_one_of_its_kind_in_place() {
        RunWorker deletion = working(RunWorkerKind.DELETION);

        assertThat(started.withWorker(deletion).workers()).containsExactly(RunWorker.idle(RunWorkerKind.REPUBLICATION),
                deletion, RunWorker.idle(RunWorkerKind.VERIFICATION));
    }

    @Test
    void a_relaunch_counts_for_its_worker_and_for_the_run() {
        AutomaticRun run = started.relaunchedBy(RunWorkerKind.DELETION).relaunchedBy(RunWorkerKind.DELETION);

        assertThat(run.relaunchCount()).isEqualTo(2);
        assertThat(run.workers()).extracting(RunWorker::relaunchCount).containsExactly(0, 2, 0);
    }

    @Test
    void a_pause_takes_effect_only_once_no_worker_is_working() {
        AutomaticRun asked = started.withWorker(working(RunWorkerKind.DELETION)).withPauseRequested();

        assertThat(asked.pausedIfRequested().status()).isEqualTo(RunStatus.RUNNING);
        AutomaticRun paused = asked.withWorker(RunWorker.idle(RunWorkerKind.DELETION).stopped()).pausedIfRequested();
        assertThat(paused.status()).isEqualTo(RunStatus.PAUSED);
        assertThat(paused.pauseRequested()).isFalse();
    }

    @Test
    void a_paused_run_stays_paused_whatever_its_workers_do() {
        AutomaticRun paused = started.withPauseRequested().pausedIfRequested();

        assertThat(paused.withWorker(RunWorker.idle(RunWorkerKind.DELETION).stopped()).status())
                .isEqualTo(RunStatus.PAUSED);
    }

    @Test
    void leaves_out_the_listings_its_workers_are_relaunching() {
        // US-41
        AutomaticRun run = started.withWorker(working(RunWorkerKind.REPUBLICATION));

        assertThat(run.leavesOut(BONNET)).isTrue();
        assertThat(run.leavesOut(new VintedListingId(2))).isFalse();
    }

    @Test
    void an_ended_run_has_its_workers_stopped() {
        AutomaticRun finished = started.withWorker(working(RunWorkerKind.DELETION)).finishedAt(NOW);
        AutomaticRun stopped = started.stoppedAt(NOW, RunStopReason.STOPPED_BY_MEMBER);

        assertThat(finished.workers()).extracting(RunWorker::state).containsOnly(RunWorkerState.STOPPED);
        assertThat(finished.workers()).extracting(RunWorker::listing).containsOnly(Optional.empty());
        assertThat(stopped.workers()).extracting(RunWorker::state).containsOnly(RunWorkerState.STOPPED);
    }
}
