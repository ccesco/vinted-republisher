package fr.vinted.republisher.domain.inventory;

import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Every listing known to the tool, present or not, with what the member needs to choose the ones to handle. */
public record ListingInventory(List<InventoryEntry> entries) {

    public ListingInventory {
        entries = List.copyOf(entries);
    }

    /** {@code readings} are the latest of each listing; a validation counts only for the backup it was given to. */
    public static ListingInventory of(List<Listing> listings, List<StatisticsReading> readings,
                                      List<ListingBackup> backups, List<BackupValidation> validations) {
        Map<VintedListingId, StatisticsReading> readingOf = readings.stream()
                .collect(Collectors.toMap(StatisticsReading::listingId, Function.identity()));
        Map<VintedListingId, ListingBackup> backupOf = backups.stream()
                .collect(Collectors.toMap(ListingBackup::id, Function.identity()));
        Map<VintedListingId, BackupValidation> validationOf = validations.stream()
                .collect(Collectors.toMap(BackupValidation::listingId, Function.identity()));
        return new ListingInventory(listings.stream().map(listing -> {
            Optional<ListingBackup> backup = Optional.ofNullable(backupOf.get(listing.id()));
            Optional<BackupValidation> validation = Optional.ofNullable(validationOf.get(listing.id()))
                    .filter(given -> backup.filter(given::holdsFor).isPresent());
            return new InventoryEntry(listing, Optional.ofNullable(readingOf.get(listing.id())), backup,
                    validation.map(BackupValidation::validatedAt));
        }).toList());
    }

    public List<InventoryEntry> select(InventoryCriteria criteria) {
        return entries.stream()
                .filter(criteria::accepts)
                .sorted(criteria.sort().order(criteria.direction()))
                .toList();
    }
}
