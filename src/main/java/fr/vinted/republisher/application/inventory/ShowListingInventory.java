package fr.vinted.republisher.application.inventory;

import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.inventory.InventoryCriteria;
import fr.vinted.republisher.domain.inventory.InventoryEntry;
import fr.vinted.republisher.domain.inventory.ListingInventory;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ListingStatisticsRepository;

import java.util.List;

/** Reads what the tool keeps, never Vinted. */
public class ShowListingInventory {

    private final ListingRepository listings;
    private final ListingStatisticsRepository statistics;
    private final ListingBackupRepository backups;
    private final BackupValidationRepository validations;

    public ShowListingInventory(ListingRepository listings, ListingStatisticsRepository statistics,
                                ListingBackupRepository backups, BackupValidationRepository validations) {
        this.listings = listings;
        this.statistics = statistics;
        this.backups = backups;
        this.validations = validations;
    }

    public List<InventoryEntry> show(InventoryCriteria criteria) {
        return ListingInventory.of(listings.findAll(), statistics.findLatest(), backups.findAll(),
                validations.findAll()).select(criteria);
    }
}
