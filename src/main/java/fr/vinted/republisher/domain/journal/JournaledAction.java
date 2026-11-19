package fr.vinted.republisher.domain.journal;

/** What the tool did, or refused to do, as kept in the journal. */
public enum JournaledAction {
    SCAN,
    WARDROBE_BACKUP,
    BACKUP_VALIDATION,
    BACKUP_VERIFICATION,
    DELETION,
    DELETION_RECHECK,
    REPUBLICATION,
    REPUBLICATION_VERIFICATION,
    STATISTICS_REFRESH
}
