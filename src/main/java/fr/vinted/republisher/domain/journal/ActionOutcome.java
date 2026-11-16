package fr.vinted.republisher.domain.journal;

/** How a journaled action ended, or that it started for an action that runs in the background. */
public enum ActionOutcome {
    STARTED,
    SUCCEEDED,
    DRY_RUN,
    REFUSED,
    IN_DOUBT,
    NOT_DONE,
    FAILED
}
