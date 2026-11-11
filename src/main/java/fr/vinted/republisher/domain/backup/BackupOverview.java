package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What the member sees of the backups: a listing missing from {@code savedAt} was never backed up, one missing from
 * {@code photoCount} has no photo kept, one missing from
 * {@code validatedAt} has no validation that holds for its current backup.
 */
public record BackupOverview(
        boolean backupInProgress,
        Optional<Instant> lastBackupAt,
        Optional<BackupProblem> problem,
        Map<VintedListingId, Instant> savedAt,
        Map<VintedListingId, Integer> photoCount,
        Map<VintedListingId, Instant> validatedAt) {

    public BackupOverview {
        Objects.requireNonNull(lastBackupAt);
        Objects.requireNonNull(problem);
        savedAt = Map.copyOf(savedAt);
        photoCount = Map.copyOf(photoCount);
        validatedAt = Map.copyOf(validatedAt);
    }
}
