package fr.vinted.republisher.application.backup;

import fr.vinted.republisher.domain.backup.BackupNotVerified;
import fr.vinted.republisher.domain.backup.BackupReplaced;
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

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * The member confirms the backup they saw. It is verified again first: files or the wardrobe may have changed since
 * the preview. A backup replaced right after is harmless: the validation holds for the version seen only.
 */
public class ValidateListingBackup {

    private final ListingRepository listings;
    private final ListingBackupRepository listingBackups;
    private final PhotoStorage photos;
    private final BackupValidationRepository validations;
    private final Clock clock;

    public ValidateListingBackup(ListingRepository listings, ListingBackupRepository listingBackups,
                                 PhotoStorage photos, BackupValidationRepository validations, Clock clock) {
        this.listings = listings;
        this.listingBackups = listingBackups;
        this.photos = photos;
        this.validations = validations;
        this.clock = clock;
    }

    /**
     * {@code seen} is the date of the backup the member saw. Throws {@link ListingNotBackedUp},
     * {@link BackupReplaced} or {@link BackupNotVerified}, and then validates nothing.
     * Answers with the version validated, even if another backup replaced it in the meantime.
     */
    public ListingBackupView validate(VintedListingId listing, Instant seen) {
        ListingBackup backup = listingBackups.find(listing).orElseThrow(() -> new ListingNotBackedUp(listing));
        if (!backup.savedAt().equals(seen)) {
            throw new BackupReplaced(listing, seen);
        }
        BackupVerification verification = BackupVerification.of(backup, listings.find(listing), photos);
        if (!verification.passed()) {
            throw new BackupNotVerified(verification);
        }
        BackupValidation validation = validations.find(listing).filter(existing -> existing.holdsFor(backup))
                .orElseGet(() -> {
                    BackupValidation newValidation = BackupValidation.of(backup, clock.instant());
                    validations.save(newValidation);
                    return newValidation;
                });
        return new ListingBackupView(backup, verification, Optional.of(validation.validatedAt()));
    }
}
