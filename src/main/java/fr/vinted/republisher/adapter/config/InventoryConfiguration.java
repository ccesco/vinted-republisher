package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.ListingStatisticsRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class InventoryConfiguration {

    @Bean
    ShowListingInventory showListingInventory(ListingRepository listings, ListingStatisticsRepository statistics,
                                              ListingBackupRepository backups,
                                              BackupValidationRepository validations) {
        return new ShowListingInventory(listings, statistics, backups, validations);
    }
}
