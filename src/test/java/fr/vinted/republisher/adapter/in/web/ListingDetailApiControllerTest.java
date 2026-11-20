package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.application.inventory.ShowListingDetail;
import fr.vinted.republisher.domain.backup.BackupVerification;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingFieldDifference;
import fr.vinted.republisher.domain.backup.ListingFormField;
import fr.vinted.republisher.domain.backup.VerificationFailure.FieldChanged;
import fr.vinted.republisher.domain.backup.VerificationFailure.ListingAbsent;
import fr.vinted.republisher.domain.backup.VerificationResult;
import fr.vinted.republisher.domain.deletion.DeletionOutcome;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.deletion.ListingDeletionId;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationId;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryListingSaleRepository;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryBackupVerificationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;

/** US-18b: the API answers as described by the OpenAPI contract. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(ListingDetailApiController.class)
class ListingDetailApiControllerTest {

    private static final Instant FIRST = Instant.parse("2026-11-01T09:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-11-02T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private InMemoryListingRepository listings;

    @Autowired
    private InMemoryListingStatisticsRepository statistics;

    @Autowired
    private InMemoryListingRepublicationRepository republications;

    @Autowired
    private InMemoryListingDeletionRepository deletions;

    @Autowired
    private InMemoryListingBackupRepository backups;

    @Autowired
    private InMemoryBackupVerificationRepository verifications;

    @Test
    void shows_the_last_verification_failure_of_the_current_backup() {
        // AT-40-12 : la fiche affiche la date et les motifs de l'échec
        listings.saveAll(List.of(Listing.seen(aListing(101, "Pull rayé", "12.5", ACTIVE), SECOND)));
        ListingBackup backup = aBackup(101, SECOND);
        backups.save(backup);
        verifications.save(VerificationResult.of(backup, new BackupVerification(List.of(new FieldChanged(
                new ListingFieldDifference(ListingFormField.SIZE, "M (207)", "L (208)")), new ListingAbsent())), NOW));

        assertThat(mvc.get().uri("/api/listings/101"))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"listing": {"stage": "VERIFICATION_FAILED"},
                         "lastVerification": {"verifiedAt": "2026-11-17T10:00:00Z", "passed": false, "failures": [
                           {"kind": "FIELD_CHANGED", "difference": {"field": "SIZE", "saved": "M (207)",
                                                                    "read": "L (208)"}},
                           {"kind": "LISTING_ABSENT"}]}}""");
    }

    @Test
    void a_verification_of_a_replaced_backup_is_not_shown() {
        listings.saveAll(List.of(Listing.seen(aListing(101, "Pull rayé", "12.5", ACTIVE), SECOND)));
        ListingBackup old = aBackup(101, FIRST);
        verifications.save(VerificationResult.of(old, new BackupVerification(List.of(new ListingAbsent())), NOW));
        backups.save(aBackup(101, SECOND));

        assertThat(mvc.get().uri("/api/listings/101")).bodyJson().doesNotHavePath("$.lastVerification");
    }

    @Test
    void shows_the_listing_its_stage_and_its_statistics_history() {
        listings.saveAll(List.of(Listing.seen(aListing(101, "Pull rayé", "12.5", ACTIVE), SECOND).absent()));
        statistics.addAll(List.of(reading(5, 1, FIRST), reading(12, 3, SECOND)));

        assertThat(mvc.get().uri("/api/listings/101"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"listing": {"vintedId": 101, "title": "Pull rayé",
                                     "price": {"amount": 12.50, "currency": "EUR"},
                                     "url": "https://vinted.test/items/101", "present": false,
                                     "stage": "GONE",
                                     "statistics": {"viewCount": 12, "favouriteCount": 3,
                                                    "readAt": "2026-11-02T09:00:00Z"}},
                         "firstSeenAt": "2026-11-02T09:00:00Z",
                         "lastSeenAt": "2026-11-02T09:00:00Z",
                         "statisticsHistory": [
                           {"viewCount": 12, "favouriteCount": 3, "readAt": "2026-11-02T09:00:00Z"},
                           {"viewCount": 5, "favouriteCount": 1, "readAt": "2026-11-01T09:00:00Z"}],
                         "journal": [],
                         "lifecycle": [
                           {"type": "FIRST_SEEN", "listingId": 101, "at": "2026-11-02T09:00:00Z"},
                           {"type": "LEFT_WARDROBE", "listingId": 101, "at": "2026-11-02T09:00:00Z"}],
                         "priceHistory": [
                           {"price": {"amount": 12.50, "currency": "EUR"}, "changedAt": "2026-11-02T09:00:00Z"}]}""");
    }

    @Test
    void detail_shows_the_first_sighting_and_the_lifecycle() {
        listings.saveAll(List.of(Listing.seen(aListing(100, "Pull rayé", "12.5", ACTIVE), FIRST).absent(),
                Listing.seen(aListing(101, "Pull rayé", "12.5", ACTIVE), SECOND)));
        deletions.save(new ListingDeletion(new ListingDeletionId(UUID.randomUUID()), new VintedListingId(100), FIRST,
                FIRST, DeletionOutcome.CONFIRMED, Optional.of(FIRST)));
        republications.save(new ListingRepublication(new ListingRepublicationId(UUID.randomUUID()),
                new VintedListingId(100), FIRST, SECOND, RepublicationOutcome.PUBLISHED, Optional.of(SECOND),
                Optional.of(new VintedListingId(101)), Optional.empty()));

        assertThat(mvc.get().uri("/api/listings/100"))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"firstSeenAt": "2026-11-01T09:00:00Z",
                         "lifecycle": [
                           {"type": "FIRST_SEEN", "listingId": 100, "at": "2026-11-01T09:00:00Z"},
                           {"type": "DELETED", "listingId": 100, "at": "2026-11-01T09:00:00Z"},
                           {"type": "REPUBLISHED", "listingId": 100, "at": "2026-11-02T09:00:00Z",
                            "newListingId": 101},
                           {"type": "FIRST_SEEN", "listingId": 101, "at": "2026-11-02T09:00:00Z"}]}""");
    }

    @Test
    void detail_shows_the_price_history() {
        listings.saveAll(List.of(Listing.seen(aListing(101, "Pull rayé", "12.5", ACTIVE), FIRST)));
        listings.saveAll(List.of(Listing.seen(aListing(101, "Pull rayé", "10", ACTIVE), SECOND)));

        assertThat(mvc.get().uri("/api/listings/101"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.priceHistory").isEqualTo(List.of(
                        Map.of("price", Map.of("amount", 10.0, "currency", "EUR"), "changedAt", "2026-11-02T09:00:00Z"),
                        Map.of("price", Map.of("amount", 12.5, "currency", "EUR"), "changedAt", "2026-11-01T09:00:00Z")));
    }

    @Test
    void detail_shows_the_republication_comparison() {
        listings.saveAll(List.of(Listing.seen(aListing(100, "Pull rayé", "12.5", ACTIVE), FIRST).absent(),
                Listing.seen(aListing(101, "Pull rayé", "12.5", ACTIVE), SECOND)));
        statistics.addAll(List.of(
                new StatisticsReading(new VintedListingId(100), new ListingStatistics(120, 8), FIRST),
                reading(30, 4, SECOND)));
        republications.save(new ListingRepublication(new ListingRepublicationId(UUID.randomUUID()),
                new VintedListingId(100), FIRST, FIRST, RepublicationOutcome.PUBLISHED, Optional.of(FIRST),
                Optional.of(new VintedListingId(101)), Optional.empty()));

        assertThat(mvc.get().uri("/api/listings/101"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.statisticsComparison").isEqualTo(Map.of(
                        "previousListingId", 100, "republishedAt", "2026-11-01T09:00:00Z",
                        "before", Map.of("viewCount", 120, "favouriteCount", 8, "readAt", "2026-11-01T09:00:00Z"),
                        "after", Map.of("viewCount", 30, "favouriteCount", 4, "readAt", "2026-11-02T09:00:00Z"),
                        "cumulatedViewCount", 150, "cumulatedFavouriteCount", 12, "previousListingCount", 1));
    }

    @Test
    void detail_shows_the_republication_advice() {
        Instant weekAndADayAgo = NOW.minus(Duration.ofDays(8));
        listings.saveAll(List.of(Listing.seen(aListing(101, "Pull rayé", "12.5", ACTIVE), weekAndADayAgo)));
        statistics.addAll(List.of(reading(40, 2, weekAndADayAgo),
                reading(46, 2, weekAndADayAgo.plus(Duration.ofDays(7)))));

        assertThat(mvc.get().uri("/api/listings/101"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.listing.republicationAdvice")
                .isEqualTo(Map.of("viewsGained", 6, "since", weekAndADayAgo.toString()));
    }

    @Test
    void unknown_listing_is_not_found() {
        // Étant donné aucune annonce 999
        // Quand la page demande GET /api/listings/999
        // Alors la réponse est 404
        assertThat(mvc.get().uri("/api/listings/999")).hasStatus(404);
    }

    @Test
    void identifier_outside_the_contract_is_refused() {
        assertThat(mvc.get().uri("/api/listings/0")).hasStatus(400);
        assertThat(mvc.get().uri("/api/listings/abc")).hasStatus(400);
    }

    private static StatisticsReading reading(int views, int favourites, Instant readAt) {
        return new StatisticsReading(new VintedListingId(101), new ListingStatistics(views, favourites), readAt);
    }

    @TestConfiguration
    static class DetailUseCase {

        @Bean
        InMemoryListingRepository listings() {
            return new InMemoryListingRepository();
        }

        @Bean
        InMemoryListingStatisticsRepository statistics() {
            return new InMemoryListingStatisticsRepository();
        }

        @Bean
        InMemoryListingRepublicationRepository republications() {
            return new InMemoryListingRepublicationRepository();
        }

        @Bean
        InMemoryListingDeletionRepository deletions() {
            return new InMemoryListingDeletionRepository();
        }

        @Bean
        InMemoryListingBackupRepository backups() {
            return new InMemoryListingBackupRepository();
        }

        @Bean
        InMemoryBackupVerificationRepository verifications() {
            return new InMemoryBackupVerificationRepository();
        }

        @Bean
        ShowListingDetail showListingDetail(InMemoryListingRepository listings,
                                            InMemoryListingStatisticsRepository statistics,
                                            InMemoryListingRepublicationRepository republications,
                                            InMemoryListingDeletionRepository deletions,
                                            InMemoryListingBackupRepository backups,
                                            InMemoryBackupVerificationRepository verifications) {
            return new ShowListingDetail(listings, statistics, backups,
                    new InMemoryBackupValidationRepository(),
                    deletions, republications,
                    new RepublicationDelay(Duration.ofMinutes(5)), Clock.fixed(NOW, ZoneOffset.UTC),
                    new InMemoryActionJournal(), verifications, new InMemoryListingSaleRepository());
        }
    }
}
