package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.List;
import java.util.Objects;

/** What one visit of a listing reads: its content, and its photos in their order (the first is the cover). */
public record ListingReading(ListingContent content, List<DownloadedPhoto> photos) {

    /** Throws {@link ListingUnreadable} without photo: Vinted asks for at least one. */
    public ListingReading {
        Objects.requireNonNull(content);
        photos = List.copyOf(photos);
        if (photos.isEmpty()) {
            throw new ListingUnreadable("no photo on " + content.id().value());
        }
    }

    public VintedListingId id() {
        return content.id();
    }
}
