package fr.vinted.republisher.domain.republication;

import java.util.List;
import java.util.Objects;

/** A manual republication prepared: the listing as it now stands, and the fields left for the member to complete. */
public record ManualRepublicationReport(RepublicationReport republication, List<UnfilledField> unfilledFields) {

    public ManualRepublicationReport {
        Objects.requireNonNull(republication);
        unfilledFields = List.copyOf(unfilledFields);
    }
}
