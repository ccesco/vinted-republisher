package fr.vinted.republisher.domain.duplicate;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

/** No other present listing has the title and the price of this one: deleting it could remove the last one. */
public class NotADuplicate extends RuntimeException {

    public NotADuplicate(VintedListingId listing) {
        super("Listing " + listing.value() + " has no twin");
    }
}
