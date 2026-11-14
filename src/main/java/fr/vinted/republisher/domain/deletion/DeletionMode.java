package fr.vinted.republisher.domain.deletion;

/** Set at startup: only a deliberate configuration allows real deletions. */
public enum DeletionMode {
    /** Every check is made and the delete button found, but nothing is clicked. */
    DRY_RUN,
    REAL
}
