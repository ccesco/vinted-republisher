package fr.vinted.republisher.application.inventory;

import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.inventory.ListingDetail;
import fr.vinted.republisher.domain.inventory.ListingInventory;
import fr.vinted.republisher.domain.inventory.ListingNotFound;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ListingStatisticsRepository;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;

import java.time.Clock;
import java.util.List;

/** Reads what the tool keeps about one listing, never Vinted. */
public class ShowListingDetail {

    private final ListingRepository listings;
    private final ListingStatisticsRepository statistics;
    private final ListingBackupRepository backups;
    private final BackupValidationRepository validations;
    private final ListingDeletionRepository deletions;
    private final ListingRepublicationRepository republications;
    private final RepublicationDelay delay;
    private final Clock clock;

    public ShowListingDetail(ListingRepository listings, ListingStatisticsRepository statistics,
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

    public ListingDetail show(VintedListingId id) {
        Listing listing = listings.find(id).orElseThrow(() -> new ListingNotFound(id));
        List<StatisticsReading> history = statistics.historyOf(id);
        ListingInventory inventory = ListingInventory.of(List.of(listing), history.stream().limit(1).toList(),
                backups.find(id).stream().toList(), validations.find(id).stream().toList(),
                deletions.findLatest(id).stream().toList(), republications.find(id).stream().toList(), delay,
                clock.instant());
        return new ListingDetail(inventory.entries().getFirst(), history);
    }
}
