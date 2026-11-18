package fr.vinted.republisher.domain.tag;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.List;
import java.util.Objects;

/** One tag put on, or taken off, every selected listing at once; each listing counts once. */
public record TagEdit(List<VintedListingId> listings, Tag tag, TagOperation operation) {

    public TagEdit {
        Objects.requireNonNull(tag);
        Objects.requireNonNull(operation);
        listings = listings.stream().distinct().toList();
        if (listings.isEmpty()) {
            throw new IllegalArgumentException("No listing selected");
        }
    }
}
