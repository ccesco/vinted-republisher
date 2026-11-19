package fr.vinted.republisher.domain.republication;

import fr.vinted.republisher.domain.backup.ListingFormField;
import java.util.Objects;
import java.util.Optional;

/** An option found neither by its identifier nor by its label, or a field missing from the form: nothing is published. */
public class FieldNotFillable extends RuntimeException {

    private final ListingFormField field;
    private final Long optionId;

    public FieldNotFillable(ListingFormField field, Optional<Long> optionId) {
        super(field + " cannot be filled" + optionId.map(id -> " (option " + id + ")").orElse(""));
        this.field = Objects.requireNonNull(field);
        this.optionId = optionId.orElse(null);
    }

    public ListingFormField field() {
        return field;
    }

    public Optional<Long> optionId() {
        return Optional.ofNullable(optionId);
    }

    /** What a manual republication leaves for the member to complete instead of refusing. */
    public UnfilledField unfilled() {
        return new UnfilledField(field, optionId());
    }
}
