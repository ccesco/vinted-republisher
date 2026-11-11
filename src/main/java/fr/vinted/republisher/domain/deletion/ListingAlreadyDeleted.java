package fr.vinted.republisher.domain.deletion;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

public class ListingAlreadyDeleted extends RuntimeException {

    public ListingAlreadyDeleted(VintedListingId listing) {
        super("Listing " + listing.value() + " is already deleted from Vinted");
    }
}
