package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.backup.BackupSummary;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.tag.Tag;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A listing with its latest statistics reading, its current backup and the date of the validation that holds for
 * that backup, where it stands, whether the tool advises republishing it, the republication that makes it republished
 * or to check (none at any other stage) with the new listing it led to when the tool knows it, the tags it bears,
 * in alphabetical order, and the republication tally of its article.
 */
public record InventoryEntry(Listing listing, Optional<StatisticsReading> statistics, Optional<BackupSummary> backup,
                             Optional<Instant> validatedAt, ListingStage stage, Optional<RepublicationAdvice> advice,
                             Optional<ListingRepublication> republication, Optional<Listing> newListing,
                             List<Tag> tags, RepublicationTally republicationTally) {

    public InventoryEntry {
        Objects.requireNonNull(listing);
        Objects.requireNonNull(statistics);
        Objects.requireNonNull(backup);
        Objects.requireNonNull(validatedAt);
        Objects.requireNonNull(stage);
        Objects.requireNonNull(advice);
        Objects.requireNonNull(republication);
        Objects.requireNonNull(newListing);
        Objects.requireNonNull(republicationTally);
        tags = tags.stream().distinct().sorted().toList();
    }

    /** Without republication, tags nor tally. */
    public InventoryEntry(Listing listing, Optional<StatisticsReading> statistics, Optional<BackupSummary> backup,
                          Optional<Instant> validatedAt, ListingStage stage, Optional<RepublicationAdvice> advice) {
        this(listing, statistics, backup, validatedAt, stage, advice, Optional.empty(), Optional.empty(), List.of(),
                RepublicationTally.NONE);
    }

    InventoryEntry withTags(List<Tag> borne) {
        return new InventoryEntry(listing, statistics, backup, validatedAt, stage, advice, republication, newListing,
                borne, republicationTally);
    }

    InventoryEntry withRepublicationTally(RepublicationTally tally) {
        return new InventoryEntry(listing, statistics, backup, validatedAt, stage, advice, republication, newListing,
                tags, tally);
    }
}
