package fr.vinted.republisher.domain.republication;

import java.util.Objects;
import java.util.UUID;

public record ListingRepublicationId(UUID value) {

    public ListingRepublicationId {
        Objects.requireNonNull(value);
    }
}
