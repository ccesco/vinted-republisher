package fr.vinted.republisher.domain.duplicate;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

/** The listing has a republication, a deletion or a sale to keep: it cannot be erased as a duplicate. */
public class ListingHasHistory extends RuntimeException {

    public ListingHasHistory(VintedListingId listing) {
        super("Listing " + listing.value() + " has history to keep");
    }
}
