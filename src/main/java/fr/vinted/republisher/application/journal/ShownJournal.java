package fr.vinted.republisher.application.journal;

import fr.vinted.republisher.domain.journal.JournalPage;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.Map;
import java.util.Objects;

/** A page of the journal with the current title of the listings it names, when the tool knows them. */
public record ShownJournal(JournalPage page, Map<VintedListingId, String> titles) {

    public ShownJournal {
        Objects.requireNonNull(page);
        titles = Map.copyOf(titles);
    }
}
