package fr.vinted.republisher.domain.deletion;

public enum DeletionOutcome {
    /** Vinted no longer shows the listing. */
    CONFIRMED,
    /** The confirmation was clicked on Vinted, but nothing proves the listing is gone. */
    UNCONFIRMED,
    /** The doubt was lifted: Vinted still shows the listing, active. */
    NOT_DONE
}
