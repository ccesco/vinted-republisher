package fr.vinted.republisher.adapter.config;

import fr.vinted.republisher.application.cadence.CadenceCheck;
import fr.vinted.republisher.adapter.out.browser.BackupProperties;
import fr.vinted.republisher.adapter.out.browser.ChromeTabs;
import fr.vinted.republisher.adapter.out.time.SleepingRunWaiting;
import fr.vinted.republisher.adapter.out.browser.PlaywrightListingPublisher;
import fr.vinted.republisher.adapter.out.browser.WardrobeProperties;
import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.application.republication.NewListingSearch;
import fr.vinted.republisher.application.republication.PrepareManualRepublication;
import fr.vinted.republisher.application.republication.RepublicationChecks;
import fr.vinted.republisher.application.republication.RepublishListing;
import fr.vinted.republisher.application.republication.ShowListingRepublication;
import fr.vinted.republisher.application.republication.ValidateListingRepublication;
import fr.vinted.republisher.application.republication.VerifyListingRepublication;
import fr.vinted.republisher.domain.backup.BackupPace;
import fr.vinted.republisher.domain.backup.BackupValidationRepository;
import fr.vinted.republisher.domain.backup.ListingBackupRepository;
import fr.vinted.republisher.domain.backup.ListingContentReader;
import fr.vinted.republisher.domain.backup.PhotoStorage;
import fr.vinted.republisher.domain.deletion.ListingDeletionRepository;
import fr.vinted.republisher.domain.republication.ListingPublisher;
import fr.vinted.republisher.domain.republication.ListingRepublicationRepository;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationMode;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.ListingRepository;
import fr.vinted.republisher.domain.wardrobe.PauseRange;
import fr.vinted.republisher.domain.wardrobe.WardrobeReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Random;

/** Wires the republication use cases: the application layer stays free of Spring. */
@Configuration
public class ListingRepublicationConfiguration {

    /** Fields and options show up within seconds on the observed form; photos take longer to be sent. */
    private static final Duration CONTROL_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration PHOTO_TIMEOUT = Duration.ofSeconds(60);
    /** Twice the tool gave up after 10 s with the form still shown: Vinted gets a full minute to publish. */
    private static final Duration PUBLICATION_TIMEOUT = Duration.ofSeconds(60);

    @Bean
    ListingPublisher listingPublisher(@Value("${vinted.listing-form-url}") URI listingFormUrl, ChromeTabs tabs) {
        return new PlaywrightListingPublisher(listingFormUrl, CONTROL_TIMEOUT, PHOTO_TIMEOUT, PUBLICATION_TIMEOUT,
                tabs);
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
                                      CadenceCheck cadence,
                                      Clock clock, ShowListingRepublication showListingRepublication,
                                      JournalWriter journal,
                                      RepublicationConfiguration.RepublicationProperties republication) {
        return new RepublishListing(session,
                new RepublicationChecks(listingBackups, photos, validations, deletions, republications, delay, clock),
                cadence,
                republications, publisher,
                new NewListingSearch(wardrobe, WardrobeConfiguration.paceOf(wardrobeProperties.scan()), listings),
                listings, mode, clock, showListingRepublication, journal,
                new SleepingRunWaiting(clock, Duration.ofSeconds(1)), republication.proofDelay());
    }
    // A manual validation reads one page at most: it runs within the request too.
    @Bean
    ValidateListingRepublication validateListingRepublication(VintedSession session,
                                                              ListingBackupRepository listingBackups,
                                                              ListingRepublicationRepository republications,
                                                              ListingRepository listings,
                                                              ListingContentReader contents, BackupProperties backup,
                                                              Clock clock, WardrobeProperties wardrobe,
                                                              ShowListingRepublication showListingRepublication,
                                                              JournalWriter journal) {
        return new ValidateListingRepublication(session, listingBackups, republications, listings, contents,
                new BackupPace(new PauseRange(backup.minPagePause(), backup.maxPagePause()), new Random()), clock,
                showListingRepublication, wardrobe.wardrobeUrl(), journal);
    }

    // A manual republication fills one form and leaves it to the member: it runs within the request.
    @Bean
    PrepareManualRepublication prepareManualRepublication(VintedSession session,
                                                          ListingBackupRepository listingBackups, PhotoStorage photos,
                                                          BackupValidationRepository validations,
                                                          ListingDeletionRepository deletions,
                                                          ListingRepublicationRepository republications,
                                                          ListingPublisher publisher, RepublicationMode mode,
                                                          RepublicationDelay delay, Clock clock,
                                                          ShowListingRepublication showListingRepublication,
                                                          JournalWriter journal) {
        return new PrepareManualRepublication(session,
                new RepublicationChecks(listingBackups, photos, validations, deletions, republications, delay, clock),
                republications, publisher, mode, clock, showListingRepublication, journal);
    }

    // A verification only reads the newest listings of the wardrobe, and the new listing once: within the request.
    @Bean
    VerifyListingRepublication verifyListingRepublication(VintedSession session, ListingBackupRepository listingBackups,
                                                          ListingRepublicationRepository republications,
                                                          ListingRepository listings, WardrobeReader wardrobe,
                                                          WardrobeProperties wardrobeProperties,
                                                          ListingContentReader contents, BackupProperties backup,
                                                          Clock clock,
                                                          ShowListingRepublication showListingRepublication,
                                                          JournalWriter journal) {
        return new VerifyListingRepublication(session, listingBackups, republications, listings,
                new NewListingSearch(wardrobe, WardrobeConfiguration.paceOf(wardrobeProperties.scan()), listings),
                contents, new BackupPace(new PauseRange(backup.minPagePause(), backup.maxPagePause()), new Random()),
                clock, showListingRepublication, journal);
    }
}
