package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.adapter.out.browser.ChromeTabs;
import fr.vinted.republisher.adapter.out.browser.PlaywrightListingDeleter;
import fr.vinted.republisher.application.deletion.DeleteListing;
import fr.vinted.republisher.application.deletion.RecheckListingDeletion;
import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.PhotoStorage;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.ListingDeleter;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/** Wires the deletion use cases: the application layer stays free of Spring. */
@Configuration
public class DeletionConfiguration {

    /** The delete button, then Vinted's dialog, show up within seconds on the observed page. */
    private static final Duration CONTROL_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    ListingDeleter listingDeleter(ChromeTabs tabs) {
        return new PlaywrightListingDeleter(CONTROL_TIMEOUT, tabs);
    }

    @Bean
    ShowListingDeletion showListingDeletion(ListingRepository listings, ListingBackupRepository listingBackups,
                                            PhotoStorage photos, BackupValidationRepository validations,
                                            ListingDeletionRepository deletions, DeletionMode mode,
                                            RepublicationDelay republicationDelay) {
        return new ShowListingDeletion(listings, listingBackups, photos, validations, deletions, mode,
                republicationDelay);
    }

    // A deletion opens one or two pages: it runs within the request, unlike scans and backups.
    @Bean
    DeleteListing deleteListing(VintedSession session, ListingRepository listings,
                                ListingBackupRepository listingBackups, PhotoStorage photos,
                                BackupValidationRepository validations, ListingDeletionRepository deletions,
                                ListingDeleter deleter, DeletionMode mode, RepublicationDelay republicationDelay,
                                Clock clock) {
        return new DeleteListing(session, listings, listingBackups, photos, validations, deletions, deleter, mode,
                republicationDelay, clock);
    }

    @Bean
    RecheckListingDeletion recheckListingDeletion(VintedSession session, ListingRepository listings,
                                                  ListingBackupRepository listingBackups, PhotoStorage photos,
                                                  BackupValidationRepository validations,
                                                  ListingDeletionRepository deletions, ListingDeleter deleter,
                                                  DeletionMode mode, RepublicationDelay republicationDelay,
                                                  Clock clock) {
        return new RecheckListingDeletion(session, listings, listingBackups, photos, validations, deletions, deleter,
                mode, republicationDelay, clock);
    }
}
