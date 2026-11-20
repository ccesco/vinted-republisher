package fr.vinted.republisher.domain.sale;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

/** No sale is recorded for this listing, so there is none to cancel. */
public class ListingNotSold extends RuntimeException {

    public ListingNotSold(VintedListingId listing) {
        super("Listing " + listing.value() + " has no sale");
    }
}
