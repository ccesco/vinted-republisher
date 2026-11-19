package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.Objects;

/**
 * What the inventory knows of the backup of a listing: enough to tell whether a validation or a verification holds
 * for it, without its content nor its photos.
 */
public record BackupSummary(VintedListingId id, Instant savedAt, int photoCount) {

    public BackupSummary {
        Objects.requireNonNull(id);
        Objects.requireNonNull(savedAt);
    }
}
