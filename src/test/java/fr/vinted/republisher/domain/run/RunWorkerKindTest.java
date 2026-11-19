package fr.vinted.republisher.domain.run;

import fr.vinted.republisher.domain.inventory.ListingStage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunWorkerKindTest {

    @Test
    void the_sequential_worker_takes_every_stage_of_the_run_in_its_order() {
        assertThat(RunWorkerKind.ALL_ACTIONS.stages()).containsExactly(ListingStage.REPUBLICATION_TO_CHECK,
                ListingStage.DELETION_TO_CHECK, ListingStage.TO_REPUBLISH, ListingStage.TO_DELETE,
                ListingStage.WAITING);
    }

    @Test
    void the_republication_worker_republishes_and_waits_for_republication_dates() {
        assertThat(RunWorkerKind.REPUBLICATION.stages())
                .containsExactly(ListingStage.TO_REPUBLISH, ListingStage.WAITING);
    }

    @Test
    void the_deletion_worker_rechecks_before_deleting() {
        assertThat(RunWorkerKind.DELETION.stages())
                .containsExactly(ListingStage.DELETION_TO_CHECK, ListingStage.TO_DELETE);
    }

    @Test
    void the_verification_worker_verifies_republications_first_then_helps_with_deletion_rechecks() {
        // AT-46-04
        assertThat(RunWorkerKind.VERIFICATION.stages())
                .containsExactly(ListingStage.REPUBLICATION_TO_CHECK, ListingStage.DELETION_TO_CHECK);
    }

    @Test
    void the_modes_have_their_workers() {
        assertThat(ParallelismMode.SEQUENTIAL.workerKinds()).containsExactly(RunWorkerKind.ALL_ACTIONS);
        assertThat(ParallelismMode.WORKERS.workerKinds()).isEqualTo(
                List.of(RunWorkerKind.REPUBLICATION, RunWorkerKind.DELETION, RunWorkerKind.VERIFICATION));
    }
}
