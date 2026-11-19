package fr.vinted.republisher.domain.backup;

/** Where the batch verification stands: none since the start, in progress, finished, or stopped by a problem. */
public enum BackupVerificationStatus {
    IDLE,
    RUNNING,
    FINISHED,
    STOPPED
}
