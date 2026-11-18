package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.tag.Tag;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A listing with its latest statistics reading, its current backup and the date of the validation that holds for
 * that backup, where it stands, whether the tool advises republishing it, and the tags it bears, in alphabetical
 * order.
 */
public record InventoryEntry(Listing listing, Optional<StatisticsReading> statistics, Optional<ListingBackup> backup,
                             Optional<Instant> validatedAt, ListingStage stage, Optional<RepublicationAdvice> advice,
                             List<Tag> tags) {

    public InventoryEntry {
        Objects.requireNonNull(listing);
        Objects.requireNonNull(statistics);
        Objects.requireNonNull(backup);
        Objects.requireNonNull(validatedAt);
        Objects.requireNonNull(stage);
        Objects.requireNonNull(advice);
        tags = tags.stream().distinct().sorted().toList();
    }

    /** Without tags. */
    public InventoryEntry(Listing listing, Optional<StatisticsReading> statistics, Optional<ListingBackup> backup,
                          Optional<Instant> validatedAt, ListingStage stage, Optional<RepublicationAdvice> advice) {
        this(listing, statistics, backup, validatedAt, stage, advice, List.of());
    }

    InventoryEntry withTags(List<Tag> borne) {
        return new InventoryEntry(listing, statistics, backup, validatedAt, stage, advice, borne);
    }
}
