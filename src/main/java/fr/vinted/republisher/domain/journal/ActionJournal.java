package fr.vinted.republisher.domain.journal;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.List;
import java.util.OptionalLong;

/** Every journaled action, kept forever. */
public interface ActionJournal {

    void write(ActionRecord record);

    /** At most {@link JournalPage#SIZE} entries written before {@code before} (all of them if empty), most recent first. */
    JournalPage page(OptionalLong before);

    /** Every entry of the listing, most recent first. */
    List<JournalEntry> of(VintedListingId listing);

    /**
     * The most recent entry of each listing named in the journal, in no particular order, entries that leave the
     * listing as it was ({@link ActionRecord#leavesFailuresAsTheyWere()}) left aside.
     */
    List<JournalEntry> lastOfEachListing();
}
