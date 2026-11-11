package fr.vinted.republisher.domain.deletion;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

/** Only a deletion in doubt can be settled. */
public class NoUnconfirmedDeletion extends RuntimeException {

    public NoUnconfirmedDeletion(VintedListingId listing) {
        super("Listing " + listing.value() + " has no unconfirmed deletion");
    }
}
