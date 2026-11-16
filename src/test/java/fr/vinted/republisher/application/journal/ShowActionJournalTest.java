package fr.vinted.republisher.application.journal;

import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;

class ShowActionJournalTest {

    private static final Instant AT = Instant.parse("2026-11-15T10:00:00Z");

    private final InMemoryActionJournal journal = new InMemoryActionJournal();
    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final ShowActionJournal showActionJournal = new ShowActionJournal(journal, listings);

    @Test
    void gives_the_page_with_the_current_title_of_each_listing() {
        listings.saveAll(List.of(Listing.seen(aListing(101, "Pull rayé", "10", ACTIVE), AT)));
        journal.write(new ActionRecord(AT, JournaledAction.SCAN, ActionOutcome.STARTED, Optional.empty(),
                Optional.empty()));
        journal.write(new ActionRecord(AT, JournaledAction.DELETION, ActionOutcome.REFUSED,
                Optional.of(new VintedListingId(101)), Optional.of("NOT_VALIDATED")));
        journal.write(new ActionRecord(AT, JournaledAction.DELETION, ActionOutcome.REFUSED,
                Optional.of(new VintedListingId(999)), Optional.of("NOT_VALIDATED")));

        ShownJournal shown = showActionJournal.show(OptionalLong.empty());

        assertThat(shown.page().entries()).hasSize(3);
        assertThat(shown.titles()).isEqualTo(Map.of(new VintedListingId(101), "Pull rayé"));
    }
}
