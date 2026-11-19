package fr.vinted.republisher.domain.backup;

/** Why a batch verification stopped before its end; the results already obtained stay. */
public enum BackupVerificationStopReason {
    SESSION_DISCONNECTED,
    BROWSER_UNAVAILABLE,
    VINTED_UNREACHABLE,
    LISTING_UNREADABLE,
    VERIFICATION_ERROR
}
