package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

/** The listing has no backup to show or validate. */
public class ListingNotBackedUp extends RuntimeException {

    public ListingNotBackedUp(VintedListingId listing) {
        super("No backup of listing " + listing.value());
    }
}
