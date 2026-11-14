package fr.vinted.republisher.domain.republication;

/** Published once the new listing is found alone in the wardrobe; failed for anything else after the click. */
public enum RepublicationOutcome {
    PUBLISHED,
    FAILED
}
