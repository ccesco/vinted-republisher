package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.Objects;

/**
 * The member saw the backup of a listing and confirmed it. It holds for that version of the backup only: once the
 * backup is replaced, the member has not seen the new one.
 */
public record BackupValidation(VintedListingId listingId, Instant backupSavedAt, Instant validatedAt) {

    public BackupValidation {
        Objects.requireNonNull(listingId);
        Objects.requireNonNull(backupSavedAt);
        Objects.requireNonNull(validatedAt);
    }

    public static BackupValidation of(ListingBackup backup, Instant validatedAt) {
        return new BackupValidation(backup.id(), backup.savedAt(), validatedAt);
    }

    public boolean holdsFor(ListingBackup backup) {
        return listingId.equals(backup.id()) && backupSavedAt.equals(backup.savedAt());
    }
}
