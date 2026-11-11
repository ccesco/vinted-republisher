package fr.vinted.republisher.domain.backup;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** What the member sees of the backup of a listing before validating it. */
public record ListingBackupView(ListingBackup backup, BackupVerification verification, Optional<Instant> validatedAt) {

    public ListingBackupView {
        Objects.requireNonNull(backup);
        Objects.requireNonNull(verification);
        Objects.requireNonNull(validatedAt);
    }
}
