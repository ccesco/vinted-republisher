package fr.vinted.republisher.domain.republication;

public enum RepublicationOutcome {
    /** The new listing was found, alone, on Vinted. */
    PUBLISHED,
    /** Publish was clicked, but nothing proves the new listing: blocks any new attempt until verified. */
    FAILED,
    /** Verified on Vinted: no listing with the saved title and price, so a new attempt is allowed. */
    NOT_PUBLISHED
}
