package fr.vinted.republisher.domain.journal;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ActionRecordTest {

    @ParameterizedTest
    @EnumSource(value = ActionOutcome.class, names = {"IN_DOUBT", "NOT_DONE", "FAILED"})
    void doubt_not_done_and_failure_are_failures(ActionOutcome outcome) {
        assertThat(record(outcome, Optional.empty()).isFailure()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ActionOutcome.class, names = {"STARTED", "SUCCEEDED", "DRY_RUN"})
    void start_success_and_dry_run_are_not_failures(ActionOutcome outcome) {
        assertThat(record(outcome, Optional.empty()).isFailure()).isFalse();
    }

    @Test
    void refusal_proper_to_the_listing_is_a_failure() {
        assertThat(record(ActionOutcome.REFUSED, Optional.of("BACKUP_REPLACED")).isFailure()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CADENCE_LIMIT_REACHED", "TOO_EARLY", "BROWSER_BUSY"})
    void refusal_asking_only_to_wait_is_not_a_failure(String reason) {
        assertThat(record(ActionOutcome.REFUSED, Optional.of(reason)).isFailure()).isFalse();
        assertThat(record(ActionOutcome.REFUSED, Optional.of(reason)).isWaitingRefusal()).isTrue();
    }

    @Test
    void only_a_refusal_can_ask_to_wait() {
        assertThat(record(ActionOutcome.REFUSED, Optional.of("BACKUP_REPLACED")).isWaitingRefusal()).isFalse();
        assertThat(record(ActionOutcome.FAILED, Optional.of("BROWSER_BUSY")).isWaitingRefusal()).isFalse();
    }

    private static ActionRecord record(ActionOutcome outcome, Optional<String> reason) {
        return new ActionRecord(Instant.parse("2026-11-18T10:00:00Z"), JournaledAction.DELETION, outcome,
                Optional.of(new VintedListingId(101)), reason);
    }
}
