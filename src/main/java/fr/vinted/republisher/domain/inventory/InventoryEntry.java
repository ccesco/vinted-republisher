package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A listing with its latest statistics reading, its current backup and the date of the validation that holds for
 * that backup, and where it stands.
 */
public record InventoryEntry(Listing listing, Optional<StatisticsReading> statistics, Optional<ListingBackup> backup,
                             Optional<Instant> validatedAt, ListingStage stage) {

    public InventoryEntry {
        Objects.requireNonNull(listing);
        Objects.requireNonNull(statistics);
        Objects.requireNonNull(backup);
        Objects.requireNonNull(validatedAt);
        Objects.requireNonNull(stage);
    }
}
