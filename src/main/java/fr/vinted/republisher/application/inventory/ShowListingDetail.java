package fr.vinted.republisher.application.inventory;

import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.inventory.ListingDetail;
import fr.vinted.republisher.domain.inventory.ListingInventory;
import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ListingStatisticsRepository;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.util.List;

/** Reads what the tool keeps about one listing, never Vinted. */
public class ShowListingDetail {

    private final ListingRepository listings;
    private final ListingStatisticsRepository statistics;
    private final ListingBackupRepository backups;
    private final BackupValidationRepository validations;

    public ShowListingDetail(ListingRepository listings, ListingStatisticsRepository statistics,
                             ListingBackupRepository backups, BackupValidationRepository validations) {
        this.listings = listings;
        this.statistics = statistics;
        this.backups = backups;
        this.validations = validations;
    }

    public ListingDetail show(VintedListingId id) {
        Listing listing = listings.find(id).orElseThrow(() -> new ListingNotFound(id));
        List<StatisticsReading> history = statistics.historyOf(id);
        ListingInventory inventory = ListingInventory.of(List.of(listing), history.stream().limit(1).toList(),
                backups.find(id).stream().toList(), validations.find(id).stream().toList());
        return new ListingDetail(inventory.entries().getFirst(), history);
    }
}
