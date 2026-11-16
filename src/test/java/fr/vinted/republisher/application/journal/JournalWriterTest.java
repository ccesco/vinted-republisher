package fr.vinted.republisher.application.journal;

import fr.vinted.republisher.domain.backup.BackupReplaced;
import fr.vinted.republisher.domain.backup.ListingNotBackedUp;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class JournalWriterTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
    private static final VintedListingId LISTING = new VintedListingId(101);

    private final InMemoryActionJournal journal = new InMemoryActionJournal();
    private final JournalWriter writer = new JournalWriter(journal, new AdjustableClock(NOW));

    @Test
    void journals_the_outcome_of_the_answer() {
        String answer = writer.journaled(JournaledAction.DELETION, LISTING, () -> "dry run",
                done -> ActionOutcome.DRY_RUN, Optional.of("DELETION_FAILED"));

        assertThat(answer).isEqualTo("dry run");
        assertThat(journal.records()).containsExactly(new ActionRecord(NOW, JournaledAction.DELETION,
                ActionOutcome.DRY_RUN, Optional.of(LISTING), Optional.empty()));
    }

    @Test
    void journals_a_refusal_with_its_api_code_and_passes_it_on() {
        BackupReplaced refusal = new BackupReplaced(LISTING, NOW);

        assertThatThrownBy(() -> writer.journaled(JournaledAction.BACKUP_VALIDATION, LISTING, () -> {
            throw refusal;
        }, done -> ActionOutcome.SUCCEEDED, Optional.empty())).isSameAs(refusal);
        assertThat(journal.records()).containsExactly(new ActionRecord(NOW, JournaledAction.BACKUP_VALIDATION,
                ActionOutcome.REFUSED, Optional.of(LISTING), Optional.of("BACKUP_REPLACED")));
    }

    @Test
    void journals_an_unexpected_failure_with_the_failure_reason() {
        IllegalStateException failure = new IllegalStateException("boom");

        assertThatThrownBy(() -> writer.journaled(JournaledAction.DELETION, LISTING, () -> {
            throw failure;
        }, done -> ActionOutcome.SUCCEEDED, Optional.of("DELETION_FAILED"))).isSameAs(failure);
        assertThat(journal.records()).singleElement().satisfies(record -> {
            assertThat(record.outcome()).isEqualTo(ActionOutcome.FAILED);
            assertThat(record.reason()).contains("DELETION_FAILED");
        });
    }

    @Test
    void listing_never_backed_up_is_not_an_action() {
        assertThatThrownBy(() -> writer.journaled(JournaledAction.DELETION, LISTING, () -> {
            throw new ListingNotBackedUp(LISTING);
        }, done -> ActionOutcome.SUCCEEDED, Optional.empty())).isInstanceOf(ListingNotBackedUp.class);
        assertThat(journal.records()).isEmpty();
    }

    @Test
    void journal_down_changes_nothing_and_is_logged(CapturedOutput logs) {
        JournalWriter downWriter = new JournalWriter(new InMemoryActionJournal().whereDatabaseIsDown(),
                new AdjustableClock(NOW));

        assertThat(downWriter.journaled(JournaledAction.DELETION, LISTING, () -> "done",
                done -> ActionOutcome.SUCCEEDED, Optional.empty())).isEqualTo("done");
        assertThat(logs.getOut()).contains("Journal not written: DELETION");
    }
}
