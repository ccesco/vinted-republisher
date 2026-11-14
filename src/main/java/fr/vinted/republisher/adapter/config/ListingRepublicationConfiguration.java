package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.adapter.out.browser.UnobservedListingPublisher;
import fr.vinted.republisher.adapter.out.browser.WardrobeProperties;
import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.application.republication.NewListingSearch;
import fr.vinted.republisher.application.republication.RepublicationChecks;
import fr.vinted.republisher.application.republication.RepublishListing;
import fr.vinted.republisher.application.republication.ShowListingRepublication;
import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.PhotoStorage;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.republication.ListingPublisher;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationMode;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.WardrobeReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Wires the republication use cases: the application layer stays free of Spring. */
@Configuration
public class ListingRepublicationConfiguration {

    @Bean
    ListingPublisher listingPublisher() {
        return new UnobservedListingPublisher();
    }

    @Bean
    ShowListingRepublication showListingRepublication(ShowListingDeletion showListingDeletion,
                                                      ListingRepublicationRepository republications,
                                                      ListingRepository listings, RepublicationMode mode) {
        return new ShowListingRepublication(showListingDeletion, republications, listings, mode);
    }

    // A republication fills one form and reads the wardrobe once: it runs within the request, like a deletion.
    @Bean
    RepublishListing republishListing(VintedSession session, ListingBackupRepository listingBackups,
                                      PhotoStorage photos, BackupValidationRepository validations,
                                      ListingDeletionRepository deletions,
                                      ListingRepublicationRepository republications, ListingPublisher publisher,
                                      WardrobeReader wardrobe, WardrobeProperties wardrobeProperties,
                                      ListingRepository listings, RepublicationMode mode, RepublicationDelay delay,
                                      Clock clock, ShowListingRepublication showListingRepublication) {
        return new RepublishListing(session,
                new RepublicationChecks(listingBackups, photos, validations, deletions, republications, delay, clock),
                republications, publisher,
                new NewListingSearch(wardrobe, WardrobeConfiguration.paceOf(wardrobeProperties.scan()), listings),
                listings, mode, clock, showListingRepublication);
    }
}
