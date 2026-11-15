package fr.vinted.republisher.domain.backup;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Where the backup of the wardrobe stands; each listing's backup is in the inventory. */
public record BackupOverview(
        boolean backupInProgress,
        Optional<Instant> lastBackupAt,
        Optional<BackupProblem> problem) {

    public BackupOverview {
        Objects.requireNonNull(lastBackupAt);
        Objects.requireNonNull(problem);
    }
}
