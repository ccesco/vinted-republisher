package fr.vinted.republisher.application.inventory;

import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.inventory.InventoryCriteria;
import fr.vinted.republisher.domain.inventory.InventoryEntry;
import fr.vinted.republisher.domain.inventory.ListingInventory;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ListingStatisticsRepository;

import java.time.Clock;
import java.util.List;

/** Reads what the tool keeps, never Vinted. */
public class ShowListingInventory {

    private final ListingRepository listings;
    private final ListingStatisticsRepository statistics;
    private final ListingBackupRepository backups;
    private final BackupValidationRepository validations;
    private final ListingDeletionRepository deletions;
    private final ListingRepublicationRepository republications;
    private final RepublicationDelay delay;
    private final Clock clock;

    public ShowListingInventory(ListingRepository listings, ListingStatisticsRepository statistics,
                                ListingBackupRepository backups, BackupValidationRepository validations,
                                ListingDeletionRepository deletions, ListingRepublicationRepository republications,
                                RepublicationDelay delay, Clock clock) {
        this.listings = listings;
        this.statistics = statistics;
        this.backups = backups;
        this.validations = validations;
        this.deletions = deletions;
        this.republications = republications;
        this.delay = delay;
        this.clock = clock;
    }

    public List<InventoryEntry> show(InventoryCriteria criteria) {
        return ListingInventory.of(listings.findAll(), statistics.findLatest(), backups.findAll(),
                validations.findAll(), deletions.findAllLatest(), republications.findAll(), delay,
                clock.instant()).select(criteria);
    }
}
