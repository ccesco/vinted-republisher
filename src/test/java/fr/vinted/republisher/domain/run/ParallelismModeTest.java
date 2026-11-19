package fr.vinted.republisher.domain.run;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelismModeTest {

    @Test
    void sequential_runs_one_listing_action_at_a_time() {
        assertThat(ParallelismMode.SEQUENTIAL.listingActions()).isEqualTo(1);
    }

    @Test
    void workers_run_one_listing_action_each() {
        // AT-46-11
        assertThat(ParallelismMode.WORKERS.listingActions()).isEqualTo(3);
    }
}
