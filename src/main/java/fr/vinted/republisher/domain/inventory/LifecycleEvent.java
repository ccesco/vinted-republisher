package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** One established fact in the life of a listing; {@code newListing} only for a republication. */
public record LifecycleEvent(LifecycleEventType type, VintedListingId listing, Instant at,
                             Optional<VintedListingId> newListing) {

    public LifecycleEvent {
        Objects.requireNonNull(type);
        Objects.requireNonNull(listing);
        Objects.requireNonNull(at);
        Objects.requireNonNull(newListing);
        if (newListing.isPresent() != (type == LifecycleEventType.REPUBLISHED)) {
            throw new IllegalArgumentException("Only a republication names a new listing");
        }
    }
}
