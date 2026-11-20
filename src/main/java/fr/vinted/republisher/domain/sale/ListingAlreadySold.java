package fr.vinted.republisher.domain.sale;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

/** A listing has one sale at most: noting it sold again is refused. */
public class ListingAlreadySold extends RuntimeException {

    public ListingAlreadySold(VintedListingId listing) {
        super("Listing " + listing.value() + " already has a sale");
    }
}
