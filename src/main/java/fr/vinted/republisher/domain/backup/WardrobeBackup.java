package fr.vinted.republisher.domain.backup;

import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One backup of every present listing, which may take several attempts: it stays unfinished until each one was
 * saved. Its start tells what it has saved, since a listing backup is dated when it is read.
 */
public record WardrobeBackup(WardrobeBackupId id, Instant startedAt, Optional<Instant> finishedAt) {

    public WardrobeBackup {
        Objects.requireNonNull(id);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(finishedAt);
    }

    public static WardrobeBackup startedAt(Instant startedAt) {
        return new WardrobeBackup(new WardrobeBackupId(UUID.randomUUID()), startedAt, Optional.empty());
    }

    public WardrobeBackup finished(Instant at) {
        return new WardrobeBackup(id, startedAt, Optional.of(at));
    }

    public List<VintedListingId> listingsToOpen(List<Listing> inventory, List<ListingBackup> backups) {
        Set<VintedListingId> saved = backups.stream().filter(this::hasSaved).map(ListingBackup::id)
                .collect(Collectors.toSet());
        return inventory.stream().filter(Listing::present).map(Listing::id).filter(id -> !saved.contains(id)).toList();
    }

    private boolean hasSaved(ListingBackup backup) {
        return !backup.savedAt().isBefore(startedAt);
    }
}
