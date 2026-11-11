package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;

/** The member asked to validate a version of the backup replaced since: they have not seen the new one. */
public class BackupReplaced extends RuntimeException {

    public BackupReplaced(VintedListingId listing, Instant seen) {
        super("The backup of listing " + listing.value() + " saved at " + seen + " has been replaced");
    }
}
