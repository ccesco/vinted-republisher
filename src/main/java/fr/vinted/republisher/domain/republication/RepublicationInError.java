package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

/** Publish was clicked without proof of the new listing: trying again could put it twice on Vinted. */
public class RepublicationInError extends RuntimeException {

    public RepublicationInError(VintedListingId listing) {
        super("The republication of listing " + listing.value() + " is in error: check on Vinted");
    }
}
