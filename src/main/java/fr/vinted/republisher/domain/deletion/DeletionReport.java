package fr.vinted.republisher.domain.deletion;

import fr.vinted.republisher.domain.backup.ListingBackupView;

import java.util.Objects;
import java.util.Optional;

/** The backup of a listing as the member sees it, with its last deletion and the mode deletions run in. */
public record DeletionReport(ListingBackupView backup, Optional<ListingDeletion> lastDeletion, DeletionMode mode,
                             boolean dryRunPassed) {

    public DeletionReport {
        Objects.requireNonNull(backup);
        Objects.requireNonNull(lastDeletion);
        Objects.requireNonNull(mode);
    }
}
