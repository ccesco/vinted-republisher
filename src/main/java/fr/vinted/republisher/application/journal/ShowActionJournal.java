package fr.vinted.republisher.application.journal;

import fr.vinted.republisher.domain.journal.ActionJournal;
import fr.vinted.republisher.domain.journal.JournalPage;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

/** Reads the journal, never Vinted. */
public class ShowActionJournal {

    private final ActionJournal journal;
    private final ListingRepository listings;

    public ShowActionJournal(ActionJournal journal, ListingRepository listings) {
        this.journal = journal;
        this.listings = listings;
    }

    /** {@code before}: the id of the last entry already seen, to get the older ones. */
    public ShownJournal show(OptionalLong before) {
        JournalPage page = journal.page(before);
        Set<VintedListingId> named = page.entries().stream()
                .flatMap(entry -> entry.record().listingId().stream())
                .collect(Collectors.toSet());
        Map<VintedListingId, String> titles = listings.findAll().stream()
                .filter(listing -> named.contains(listing.id()))
                .collect(Collectors.toMap(Listing::id, Listing::title));
        return new ShownJournal(page, titles);
    }
}
