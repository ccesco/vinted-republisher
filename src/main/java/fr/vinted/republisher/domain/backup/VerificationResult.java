package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The last verification of a backup against Vinted (US-40), kept for the listing. It holds for the version of the
 * backup it compared only: a new backup has not been verified.
 */
public record VerificationResult(VintedListingId listingId, Instant backupSavedAt, Instant verifiedAt,
                                 List<VerificationFailure> failures) {

    public VerificationResult {
        Objects.requireNonNull(listingId);
        Objects.requireNonNull(backupSavedAt);
        Objects.requireNonNull(verifiedAt);
        failures = List.copyOf(failures);
    }

    public static VerificationResult of(ListingBackup backup, BackupVerification verification, Instant verifiedAt) {
        return new VerificationResult(backup.id(), backup.savedAt(), verifiedAt, verification.failures());
    }

    public boolean passed() {
        return failures.isEmpty();
    }

    public boolean holdsFor(BackupSummary summary) {
        return listingId.equals(summary.id()) && backupSavedAt.equals(summary.savedAt());
    }

    /** The backup that {@code summary} sums up failed its last verification. */
    public boolean failsFor(BackupSummary summary) {
        return holdsFor(summary) && !passed();
    }
}
