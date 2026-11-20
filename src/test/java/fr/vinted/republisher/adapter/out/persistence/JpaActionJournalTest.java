package fr.vinted.republisher.adapter.out.persistence;

import fr.vinted.republisher.ApplicationWithDatabaseTest;
import fr.vinted.republisher.domain.journal.ActionJournal;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournalEntry;
import fr.vinted.republisher.domain.journal.JournalPage;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** On a disposable PostgreSQL with the real changelog, never the real data. */
class JpaActionJournalTest extends ApplicationWithDatabaseTest {

    private static final Instant AT = Instant.parse("2026-11-15T10:00:00.123456Z");
    private static final VintedListingId LISTING = new VintedListingId(10208778166L);
    private static final VintedListingId OTHER = new VintedListingId(10208778167L);
    private static final VintedListingId THIRD = new VintedListingId(10208778168L);

    @Autowired
    private ActionJournal journal;

    @Test
    void entries_survive_restart() {
        // US-23 AT-23-15. Étant donné des lignes écrites dans le journal
        ActionRecord scan = new ActionRecord(AT, JournaledAction.SCAN, ActionOutcome.STARTED, Optional.empty(),
                Optional.empty());
        ActionRecord refusal = new ActionRecord(AT.plusSeconds(1), JournaledAction.DELETION, ActionOutcome.REFUSED,
                Optional.of(LISTING), Optional.of("NOT_VALIDATED"));
        journal.write(scan);
        journal.write(refusal);
        // Quand l'outil redémarre (rien n'est gardé en mémoire : tout est relu en base)
        // Alors elles sont relues à l'identique, dans l'ordre d'écriture
        assertThat(journal.page(OptionalLong.empty()).entries()).extracting(JournalEntry::record)
                .containsExactly(refusal, scan);
        assertThat(journal.page(OptionalLong.empty()).hasMore()).isFalse();
    }

    @Test
    void pages_of_fifty_from_the_most_recent() {
        IntStream.range(0, 120).forEach(rank -> journal.write(new ActionRecord(AT.plusSeconds(rank),
                JournaledAction.SCAN, ActionOutcome.STARTED, Optional.empty(), Optional.empty())));

        JournalPage first = journal.page(OptionalLong.empty());
        JournalPage second = journal.page(OptionalLong.of(first.entries().getLast().id()));
        JournalPage third = journal.page(OptionalLong.of(second.entries().getLast().id()));

        assertThat(first.entries()).hasSize(50);
        assertThat(first.hasMore()).isTrue();
        assertThat(first.entries().getFirst().record().occurredAt()).isEqualTo(AT.plusSeconds(119));
        assertThat(second.entries()).hasSize(50);
        assertThat(second.hasMore()).isTrue();
        assertThat(third.entries()).hasSize(20);
        assertThat(third.hasMore()).isFalse();
        assertThat(third.entries().getLast().record().occurredAt()).isEqualTo(AT);
    }

    @Test
    void entries_of_one_listing_most_recent_first() {
        ActionRecord first = new ActionRecord(AT, JournaledAction.BACKUP_VALIDATION, ActionOutcome.SUCCEEDED,
                Optional.of(LISTING), Optional.empty());
        ActionRecord other = new ActionRecord(AT.plusSeconds(1), JournaledAction.DELETION, ActionOutcome.DRY_RUN,
                Optional.of(OTHER), Optional.empty());
        ActionRecord second = new ActionRecord(AT.plusSeconds(2), JournaledAction.DELETION, ActionOutcome.DRY_RUN,
                Optional.of(LISTING), Optional.empty());
        List.of(first, other, second).forEach(journal::write);

        assertThat(journal.of(LISTING)).extracting(JournalEntry::record).containsExactly(second, first);
    }

    @Test
    void every_journaled_action_can_be_written() {
        // US-40 AT-40-11 : la vérification d'une sauvegarde est inscrite au journal, avec ses motifs
        List<ActionRecord> records = Stream.of(JournaledAction.values())
                .map(action -> new ActionRecord(AT, action, ActionOutcome.FAILED, Optional.of(LISTING),
                        Optional.of("FIELD_CHANGED:SIZE, PHOTO_MISSING")))
                .toList();

        records.forEach(journal::write);

        assertThat(journal.of(LISTING)).extracting(JournalEntry::record).containsExactlyInAnyOrderElementsOf(records);
    }

    @Test
    void last_entry_of_each_listing() {
        // US-39 : la dernière ligne de chaque annonce, sans les lignes sans annonce ni les refus d'attente
        ActionRecord first = new ActionRecord(AT, JournaledAction.DELETION, ActionOutcome.FAILED,
                Optional.of(LISTING), Optional.of("VINTED_UNREACHABLE"));
        ActionRecord other = new ActionRecord(AT.plusSeconds(1), JournaledAction.DELETION, ActionOutcome.IN_DOUBT,
                Optional.of(OTHER), Optional.empty());
        ActionRecord second = new ActionRecord(AT.plusSeconds(2), JournaledAction.DELETION, ActionOutcome.SUCCEEDED,
                Optional.of(LISTING), Optional.empty());
        ActionRecord scan = new ActionRecord(AT.plusSeconds(3), JournaledAction.SCAN, ActionOutcome.STARTED,
                Optional.empty(), Optional.empty());
        ActionRecord waiting = new ActionRecord(AT.plusSeconds(4), JournaledAction.DELETION, ActionOutcome.REFUSED,
                Optional.of(OTHER), Optional.of("CADENCE_LIMIT_REACHED"));
        ActionRecord refusedWithoutReason = new ActionRecord(AT.plusSeconds(5), JournaledAction.DELETION,
                ActionOutcome.REFUSED, Optional.of(THIRD), Optional.empty());
        // US-48 : une recherche de la nouvelle annonce restée sans résultat ne compte pas non plus
        ActionRecord searchWithoutResult = new ActionRecord(AT.plusSeconds(6),
                JournaledAction.REPUBLICATION_VERIFICATION, ActionOutcome.NOT_DONE, Optional.of(OTHER),
                Optional.of("NEW_LISTING_NOT_FOUND"));
        List.of(first, other, second, scan, waiting, refusedWithoutReason, searchWithoutResult)
                .forEach(journal::write);

        assertThat(journal.lastOfEachListing()).extracting(JournalEntry::record)
                .containsExactlyInAnyOrder(second, other, refusedWithoutReason);
    }
}
