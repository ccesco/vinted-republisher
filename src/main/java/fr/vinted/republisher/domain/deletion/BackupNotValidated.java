package fr.vinted.republisher.domain.deletion;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

/** The member has not validated the current backup of the listing: it cannot be deleted. */
public class BackupNotValidated extends RuntimeException {

    public BackupNotValidated(VintedListingId listing) {
        super("The current backup of listing " + listing.value() + " is not validated");
    }
}
