package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.backup.ListingFormField;

import java.util.Objects;
import java.util.Optional;

/** A field of the listing form a manual republication could not fill: the member completes it before publishing. */
public record UnfilledField(ListingFormField field, Optional<Long> optionId) {

    public UnfilledField {
        Objects.requireNonNull(field);
        Objects.requireNonNull(optionId);
    }
}
