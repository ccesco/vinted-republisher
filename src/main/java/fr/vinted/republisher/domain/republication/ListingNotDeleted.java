package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

/** Only a listing whose last deletion is confirmed, on the current backup, may be republished. */
public class ListingNotDeleted extends RuntimeException {

    public ListingNotDeleted(VintedListingId listing) {
        super("Listing " + listing.value() + " has no confirmed deletion of its current backup");
    }
}
