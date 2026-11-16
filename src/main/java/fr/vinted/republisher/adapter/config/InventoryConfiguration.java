package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.application.inventory.ShowListingDetail;
import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.journal.ActionJournal;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ListingStatisticsRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class InventoryConfiguration {

    @Bean
    ShowListingInventory showListingInventory(ListingRepository listings, ListingStatisticsRepository statistics,
                                              ListingBackupRepository backups,
                                              BackupValidationRepository validations,
                                              ListingDeletionRepository deletions,
                                              ListingRepublicationRepository republications,
                                              RepublicationDelay republicationDelay, Clock clock) {
        return new ShowListingInventory(listings, statistics, backups, validations, deletions, republications,
                republicationDelay, clock);
    }

    @Bean
    ShowListingDetail showListingDetail(ListingRepository listings, ListingStatisticsRepository statistics,
                                        ListingBackupRepository backups, BackupValidationRepository validations,
                                        ListingDeletionRepository deletions,
                                        ListingRepublicationRepository republications,
                                        RepublicationDelay republicationDelay, Clock clock,
                                        ActionJournal journal) {
        return new ShowListingDetail(listings, statistics, backups, validations, deletions, republications,
                republicationDelay, clock, journal);
    }
}
