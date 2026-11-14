package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.adapter.out.browser.BackupProperties;
import fr.vinted.republisher.adapter.out.browser.ChromeTabs;
import fr.vinted.republisher.adapter.out.browser.PlaywrightListingContentReader;
import fr.vinted.republisher.adapter.out.storage.FilePhotoStorage;
import fr.vinted.republisher.application.backup.BackUpWardrobe;
import fr.vinted.republisher.application.backup.ShowBackup;
import fr.vinted.republisher.application.backup.ShowListingBackupPhoto;
import fr.vinted.republisher.application.backup.ValidateListingBackup;
import fr.vinted.republisher.domain.backup.BackupPace;
import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.LastBackupProblem;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.ListingContentReader;
import fr.vinted.republisher.domain.backup.PhotoStorage;
import fr.vinted.republisher.domain.backup.WardrobeBackupRepository;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.PauseRange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Random;
import java.util.concurrent.Executor;

/** Wires the backup use cases: the application layer stays free of Spring. */
@Configuration
@EnableConfigurationProperties(BackupProperties.class)
public class BackupConfiguration {

    @Bean
    LastBackupProblem lastBackupProblem() {
        return new LastBackupProblem();
    }

    @Bean
    ListingContentReader listingContentReader(BackupProperties backup, ChromeTabs tabs, Clock clock) {
        return new PlaywrightListingContentReader(backup.editFormUrl(), backup.listingPageUrl(), backup.labelTimeout(),
                backup.photoTimeout(), tabs, clock);
    }

    @Bean
    ShowBackup showBackup(VintedSession session, LastBackupProblem lastBackupProblem,
                          WardrobeBackupRepository backups, ListingBackupRepository listingBackups,
                          BackupValidationRepository validations) {
        return new ShowBackup(session, lastBackupProblem, backups, listingBackups, validations);
    }

    @Bean
    ValidateListingBackup validateListingBackup(ListingRepository listings, ListingBackupRepository listingBackups,
                                                PhotoStorage photos, BackupValidationRepository validations,
                                                Clock clock) {
        return new ValidateListingBackup(listings, listingBackups, photos, validations, clock);
    }

    @Bean
    ShowListingBackupPhoto showListingBackupPhoto(ListingBackupRepository listingBackups, PhotoStorage photos) {
        return new ShowListingBackupPhoto(listingBackups, photos);
    }

    @Bean
    PhotoStorage photoStorage(@Value("${app.data-dir}") Path dataDirectory) {
        return new FilePhotoStorage(dataDirectory.resolve("photos"));
    }

    @Bean
    BackUpWardrobe backUpWardrobe(VintedSession session, LastBackupProblem lastBackupProblem,
                                  WardrobeBackupRepository backups, ListingContentReader reader,
                                  ListingRepository listings, ListingBackupRepository listingBackups,
                                  PhotoStorage photos, BackupProperties backup, Clock clock,
                                  @Qualifier("applicationTaskExecutor") Executor background) {
        BackupPace pace = new BackupPace(new PauseRange(backup.minPagePause(), backup.maxPagePause()),
                new Random());
        return new BackUpWardrobe(session, lastBackupProblem, backups, reader, listings, listingBackups, photos, pace,
                clock, new FailureLoggingExecutor(background));
    }
}
