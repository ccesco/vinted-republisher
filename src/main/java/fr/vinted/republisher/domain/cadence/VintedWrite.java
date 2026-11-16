package fr.vinted.republisher.domain.cadence;

/** An action that writes on Vinted, counted by the write cadence from the moment the tool clicks. */
public enum VintedWrite {
    DELETION,
    REPUBLICATION
}
