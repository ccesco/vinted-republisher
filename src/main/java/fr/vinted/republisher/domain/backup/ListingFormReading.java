package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.Objects;
import java.util.Optional;

/** The edit form of a listing read again on Vinted: its content, or none when the form answers 404 (listing gone). */
public record ListingFormReading(VintedListingId id, Optional<ListingContent> content) {

    public ListingFormReading {
        Objects.requireNonNull(id);
        Objects.requireNonNull(content);
    }
}
