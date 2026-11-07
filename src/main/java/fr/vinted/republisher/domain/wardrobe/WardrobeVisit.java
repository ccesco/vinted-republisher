package fr.vinted.republisher.domain.wardrobe;

import java.util.List;

/** What a scan makes of what the {@link WardrobeReader} finds, as it finds it. */
public interface WardrobeVisit {

    /** Throws {@link WardrobeUnreadable} when the listing of the wardrobe cannot be trusted: nothing is opened then. */
    List<ListingLink> listingsToOpen(WardrobeListing wardrobe);

    void record(ScannedListing listing);
}
