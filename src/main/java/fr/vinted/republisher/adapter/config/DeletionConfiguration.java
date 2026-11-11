package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.adapter.out.browser.UnobservedListingDeleter;
import fr.vinted.republisher.application.deletion.DeleteListing;
import fr.vinted.republisher.application.deletion.RecheckListingDeletion;
import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.PhotoStorage;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.ListingDeleter;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Wires the deletion use cases: the application layer stays free of Spring. */
@Configuration
public class DeletionConfiguration {

    @Bean
    ListingDeleter listingDeleter() {
        return new UnobservedListingDeleter();
    }

    @Bean
    ShowListingDeletion showListingDeletion(ListingRepository listings, ListingBackupRepository listingBackups,
                                            PhotoStorage photos, BackupValidationRepository validations,
                                            ListingDeletionRepository deletions, DeletionMode mode) {
        return new ShowListingDeletion(listings, listingBackups, photos, validations, deletions, mode);
    }

    // A deletion opens one or two pages: it runs within the request, unlike scans and backups.
    @Bean
    DeleteListing deleteListing(VintedSession session, ListingRepository listings,
                                ListingBackupRepository listingBackups, PhotoStorage photos,
                                BackupValidationRepository validations, ListingDeletionRepository deletions,
                                ListingDeleter deleter, DeletionMode mode, Clock clock) {
        return new DeleteListing(session, listings, listingBackups, photos, validations, deletions, deleter, mode,
                clock);
    }

    @Bean
    RecheckListingDeletion recheckListingDeletion(VintedSession session, ListingRepository listings,
                                                  ListingBackupRepository listingBackups, PhotoStorage photos,
                                                  BackupValidationRepository validations,
                                                  ListingDeletionRepository deletions, ListingDeleter deleter,
                                                  DeletionMode mode, Clock clock) {
        return new RecheckListingDeletion(session, listings, listingBackups, photos, validations, deletions, deleter,
                mode, clock);
    }
}
