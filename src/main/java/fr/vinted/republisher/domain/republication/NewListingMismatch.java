package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

/** The listing the member pointed to does not have the saved title and price: it is not the new listing. */
public class NewListingMismatch extends RuntimeException {

    public NewListingMismatch(VintedListingId newListing) {
        super("Listing " + newListing.value() + " has not the saved title and price");
    }
}
