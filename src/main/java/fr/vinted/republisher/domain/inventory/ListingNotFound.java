package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

/** The tool knows no listing with this identifier. */
public class ListingNotFound extends RuntimeException {

    public ListingNotFound(VintedListingId listing) {
        super("Unknown listing " + listing.value());
    }
}
