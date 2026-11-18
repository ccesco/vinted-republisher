package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.application.inventory.ShowListingDetail;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.ListingRepublicationId;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
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
                         "lastSeenAt": "2026-11-02T09:00:00Z",
                         "statisticsHistory": [
                           {"viewCount": 12, "favouriteCount": 3, "readAt": "2026-11-02T09:00:00Z"},
                           {"viewCount": 5, "favouriteCount": 1, "readAt": "2026-11-01T09:00:00Z"}],
                         "journal": []}""");
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
        ShowListingDetail showListingDetail(InMemoryListingRepository listings,
                                            InMemoryListingStatisticsRepository statistics,
                                            InMemoryListingRepublicationRepository republications) {
            return new ShowListingDetail(listings, statistics, new InMemoryListingBackupRepository(),
                    new InMemoryBackupValidationRepository(),
                    new InMemoryListingDeletionRepository(), republications,
                    new RepublicationDelay(Duration.ofMinutes(5)), Clock.fixed(NOW, ZoneOffset.UTC),
                    new InMemoryActionJournal());
        }
    }
}
