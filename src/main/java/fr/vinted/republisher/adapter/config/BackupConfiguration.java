package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.adapter.out.browser.BackupProperties;
import fr.vinted.republisher.adapter.out.browser.ChromeTabs;
import fr.vinted.republisher.adapter.out.browser.PlaywrightListingContentReader;
import fr.vinted.republisher.application.backup.BackUpWardrobe;
import fr.vinted.republisher.application.backup.ShowBackup;
import fr.vinted.republisher.domain.backup.BackupPace;
import fr.vinted.republisher.domain.backup.LastBackupProblem;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.ListingContentReader;
import fr.vinted.republisher.domain.backup.WardrobeBackupRepository;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.PauseRange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
        return new PlaywrightListingContentReader(backup.editFormUrl(), backup.labelTimeout(), tabs, clock);
    }

    @Bean
    ShowBackup showBackup(VintedSession session, LastBackupProblem lastBackupProblem,
                          WardrobeBackupRepository backups, ListingBackupRepository listingBackups) {
        return new ShowBackup(session, lastBackupProblem, backups, listingBackups);
    }

    @Bean
    BackUpWardrobe backUpWardrobe(VintedSession session, LastBackupProblem lastBackupProblem,
                                  WardrobeBackupRepository backups, ListingContentReader reader,
                                  ListingRepository listings, ListingBackupRepository listingBackups,
                                  BackupProperties backup, Clock clock,
                                  @Qualifier("applicationTaskExecutor") Executor background) {
        BackupPace pace = new BackupPace(new PauseRange(backup.minListingPause(), backup.maxListingPause()),
                new Random());
        return new BackUpWardrobe(session, lastBackupProblem, backups, reader, listings, listingBackups, pace, clock,
                new FailureLoggingExecutor(background));
    }
}
