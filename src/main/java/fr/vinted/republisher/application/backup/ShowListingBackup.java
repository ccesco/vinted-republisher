package fr.vinted.republisher.application.backup;

import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.BackupVerification;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.ListingBackupView;
import fr.vinted.republisher.domain.backup.ListingNotBackedUp;
import fr.vinted.republisher.domain.backup.PhotoStorage;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

/** The backup of a listing, verified afresh each time it is shown. */
public class ShowListingBackup {

    private final ListingRepository listings;
    private final ListingBackupRepository listingBackups;
    private final PhotoStorage photos;
    private final BackupValidationRepository validations;

    public ShowListingBackup(ListingRepository listings, ListingBackupRepository listingBackups, PhotoStorage photos,
                             BackupValidationRepository validations) {
        this.listings = listings;
        this.listingBackups = listingBackups;
        this.photos = photos;
        this.validations = validations;
    }

    /** Throws {@link ListingNotBackedUp} if the listing has no backup. */
    public ListingBackupView show(VintedListingId listing) {
        ListingBackup backup = listingBackups.find(listing).orElseThrow(() -> new ListingNotBackedUp(listing));
        return new ListingBackupView(backup, BackupVerification.of(backup, listings.find(listing), photos),
                validations.find(listing).filter(validation -> validation.holdsFor(backup))
                        .map(BackupValidation::validatedAt));
    }
}
