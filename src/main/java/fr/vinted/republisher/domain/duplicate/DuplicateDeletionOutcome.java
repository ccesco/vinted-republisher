package fr.vinted.republisher.domain.duplicate;

/** How the deletion of a duplicate ended. */
public enum DuplicateDeletionOutcome {
    /** Deleted on Vinted, then erased from the tool. */
    DELETED,
    /** Vinted no longer showed it: erased from the tool without any click. */
    ALREADY_GONE,
    /** Dry run: both pages read, nothing clicked nor erased. */
    DRY_RUN_PASSED,
    /** Still shown by Vinted after the confirmation, or a failure after it: nothing erased, to check on Vinted. */
    UNCONFIRMED
}
