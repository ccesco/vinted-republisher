package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournalEntry;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;

class FailedListingTest {

    private static final Instant AT = Instant.parse("2026-11-18T10:00:00Z");

    @Test
    void keeps_listings_whose_last_entry_is_a_failure_most_recent_first() {
        List<FailedListing> failed = FailedListing.among(
                List.of(entry(101, ListingStage.TO_DELETE), entry(102, ListingStage.TO_DELETE),
                        entry(103, ListingStage.TO_DELETE)),
                List.of(journalEntry(1, 101, ActionOutcome.FAILED), journalEntry(2, 102, ActionOutcome.SUCCEEDED),
                        journalEntry(3, 103, ActionOutcome.IN_DOUBT)));

        assertThat(failed).containsExactly(
                new FailedListing(listing(103), ListingStage.TO_DELETE, journalEntry(3, 103, ActionOutcome.IN_DOUBT)),
                new FailedListing(listing(101), ListingStage.TO_DELETE, journalEntry(1, 101, ActionOutcome.FAILED)));
    }

    @Test
    void ignores_listings_gone_from_vinted() {
        assertThat(FailedListing.among(List.of(entry(101, ListingStage.GONE)),
                List.of(journalEntry(1, 101, ActionOutcome.FAILED)))).isEmpty();
    }

    @Test
    void ignores_listings_republished_since() {
        assertThat(FailedListing.among(List.of(entry(101, ListingStage.REPUBLISHED)),
                List.of(journalEntry(1, 101, ActionOutcome.FAILED)))).isEmpty();
    }

    @Test
    void ignores_entries_of_listings_unknown_to_the_tool() {
        assertThat(FailedListing.among(List.of(), List.of(journalEntry(1, 999, ActionOutcome.FAILED)))).isEmpty();
    }

    private static InventoryEntry entry(long id, ListingStage stage) {
        return new InventoryEntry(listing(id), Optional.empty(), Optional.empty(), Optional.empty(), stage,
                Optional.empty());
    }

    private static Listing listing(long id) {
        return Listing.seen(aListing(id, "Annonce " + id, "10", ACTIVE), AT);
    }

    private static JournalEntry journalEntry(long entryId, long listingId, ActionOutcome outcome) {
        return new JournalEntry(entryId, new ActionRecord(AT, JournaledAction.DELETION, outcome,
                Optional.of(new VintedListingId(listingId)), Optional.empty()));
    }
}
