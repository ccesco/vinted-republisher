package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

public class ListingAlreadyRepublished extends RuntimeException {

    public ListingAlreadyRepublished(VintedListingId listing) {
        super("Listing " + listing.value() + " is already republished on Vinted");
    }
}
