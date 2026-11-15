package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.application.inventory.ShowListingDetail;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.StatisticsReading;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;

/** US-18b: the API answers as described by the OpenAPI contract. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(ListingDetailApiController.class)
class ListingDetailApiControllerTest {

    private static final Instant FIRST = Instant.parse("2026-11-01T09:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-11-02T09:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private InMemoryListingRepository listings;

    @Autowired
    private InMemoryListingStatisticsRepository statistics;

    @Test
    void shows_the_listing_and_its_statistics_history() {
        listings.saveAll(List.of(Listing.seen(aListing(101, "Pull rayé", "12.5", ACTIVE), SECOND).absent()));
        statistics.addAll(List.of(reading(5, 1, FIRST), reading(12, 3, SECOND)));

        assertThat(mvc.get().uri("/api/listings/101"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"listing": {"vintedId": 101, "title": "Pull rayé",
                                     "price": {"amount": 12.50, "currency": "EUR"},
                                     "url": "https://vinted.test/items/101", "present": false,
                                     "statistics": {"viewCount": 12, "favouriteCount": 3,
                                                    "readAt": "2026-11-02T09:00:00Z"}},
                         "lastSeenAt": "2026-11-02T09:00:00Z",
                         "statisticsHistory": [
                           {"viewCount": 12, "favouriteCount": 3, "readAt": "2026-11-02T09:00:00Z"},
                           {"viewCount": 5, "favouriteCount": 1, "readAt": "2026-11-01T09:00:00Z"}]}""");
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
        ShowListingDetail showListingDetail(InMemoryListingRepository listings,
                                            InMemoryListingStatisticsRepository statistics) {
            return new ShowListingDetail(listings, statistics, new InMemoryListingBackupRepository(),
                    new InMemoryBackupValidationRepository());
        }
    }
}
