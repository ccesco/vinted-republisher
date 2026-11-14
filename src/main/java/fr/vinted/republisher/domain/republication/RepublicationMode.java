package fr.vinted.republisher.domain.republication;

/** Set at startup by REPUBLICATION_DRY_RUN: in dry-run the form is filled but never published, nothing is kept. */
public enum RepublicationMode {
    DRY_RUN,
    REAL
}
