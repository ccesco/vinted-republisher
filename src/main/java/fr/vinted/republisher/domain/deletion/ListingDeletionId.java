package fr.vinted.republisher.domain.deletion;

import java.util.Objects;
import java.util.UUID;

public record ListingDeletionId(UUID value) {

    public ListingDeletionId {
        Objects.requireNonNull(value);
    }
}
