package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.Objects;

/** The one backup of a listing, replaced only by a complete reading. */
public record ListingBackup(ListingContent content, Instant savedAt) {

    public ListingBackup {
        Objects.requireNonNull(content);
        Objects.requireNonNull(savedAt);
    }

    public VintedListingId id() {
        return content.id();
    }
}
