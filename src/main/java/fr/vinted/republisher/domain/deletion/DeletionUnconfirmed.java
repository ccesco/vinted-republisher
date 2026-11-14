package fr.vinted.republisher.domain.deletion;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

/** Nothing proves the last deletion of the listing happened: it must be checked again on Vinted first. */
public class DeletionUnconfirmed extends RuntimeException {

    public DeletionUnconfirmed(VintedListingId listing) {
        super("The last deletion of listing " + listing.value() + " is unconfirmed");
    }
}
