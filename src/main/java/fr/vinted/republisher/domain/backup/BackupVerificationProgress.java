package fr.vinted.republisher.domain.backup;

import java.util.Objects;
import java.util.Optional;

/**
 * How far the batch verification of the backups (US-40) has gone: listings verified out of those fixed at its launch,
 * how many passed (and were validated) or failed, and why it stopped if it did. Kept in memory only.
 */
public record BackupVerificationProgress(BackupVerificationStatus status, int verifiedCount, int totalCount,
                                         int validatedCount, int failedCount,
                                         Optional<BackupVerificationStopReason> stopReason) {

    public BackupVerificationProgress {
        Objects.requireNonNull(status);
        Objects.requireNonNull(stopReason);
    }

    public static BackupVerificationProgress idle() {
        return new BackupVerificationProgress(BackupVerificationStatus.IDLE, 0, 0, 0, 0, Optional.empty());
    }

    public static BackupVerificationProgress startedFor(int totalCount) {
        return new BackupVerificationProgress(BackupVerificationStatus.RUNNING, 0, totalCount, 0, 0, Optional.empty());
    }

    public boolean inProgress() {
        return status == BackupVerificationStatus.RUNNING;
    }

    public BackupVerificationProgress verified(boolean passed) {
        return new BackupVerificationProgress(status, verifiedCount + 1, totalCount,
                validatedCount + (passed ? 1 : 0), failedCount + (passed ? 0 : 1), stopReason);
    }

    public BackupVerificationProgress finished() {
        return new BackupVerificationProgress(BackupVerificationStatus.FINISHED, verifiedCount, totalCount,
                validatedCount, failedCount, Optional.empty());
    }

    public BackupVerificationProgress stopped(BackupVerificationStopReason reason) {
        return new BackupVerificationProgress(BackupVerificationStatus.STOPPED, verifiedCount, totalCount,
                validatedCount, failedCount, Optional.of(reason));
    }
}
