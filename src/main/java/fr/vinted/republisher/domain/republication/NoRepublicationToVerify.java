package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

/** Only a republication in error or published can be verified on Vinted. */
public class NoRepublicationToVerify extends RuntimeException {

    public NoRepublicationToVerify(VintedListingId listing) {
        super("Listing " + listing.value() + " has no republication in error or published to verify");
    }
}
