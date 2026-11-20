package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.application.cadence.CadenceCheck;
import fr.vinted.republisher.application.duplicate.DeleteDuplicate;
import fr.vinted.republisher.application.duplicate.ShowDuplicates;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.domain.cadence.WriteCadence;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.session.BrowserUnavailable;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.fakes.FakeVintedPages;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingEraser;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingSaleRepository;
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
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

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;

/** US-47: the API of the duplicates answers as described by the OpenAPI contract. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(DuplicatesApiController.class)
class DuplicateApiControllerTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-11-18T10:05:00Z");
    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private InMemoryListingRepository listings;

    @Autowired
    private FakeVintedPages vinted;

    @Test
    void shows_the_groups_of_duplicates() {
        twoDuplicates();

        assertThat(mvc.get().uri("/api/duplicates"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"deletionDryRun": false, "groups": [{"title": "Robe bleue",
                          "price": {"amount": 12.00, "currency": "EUR"},
                          "listings": [
                            {"listingId": 1, "url": "https://vinted.test/items/1",
                             "firstSeenAt": "2026-11-18T10:05:00Z", "backedUp": false, "deletable": true},
                            {"listingId": 2, "url": "https://vinted.test/items/2",
                             "firstSeenAt": "2026-11-18T10:05:00Z", "backedUp": false, "deletable": true}]}]}""");
    }

    @Test
    void deletes_a_duplicate_and_names_the_twin_kept() {
        twoDuplicates();

        assertThat(mvc.post().uri("/api/listings/2/duplicate-deletion"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("{\"outcome\": \"DELETED\", \"twinListingId\": 1}");
    }

    @Test
    void a_listing_without_twin_is_refused() {
        listings.saveAll(List.of(Listing.seen(aListing(1, "Robe bleue", "12.00", ACTIVE), SCANNED_AT)));

        assertThat(mvc.post().uri("/api/listings/1/duplicate-deletion"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"NOT_A_DUPLICATE\"}");
    }

    @Test
    void a_twin_not_confirmed_on_vinted_is_refused() {
        twoDuplicates();
        vinted.showing(aListing(1, "Robe bleue", "10.00", ACTIVE));

        assertThat(mvc.post().uri("/api/listings/2/duplicate-deletion"))
                .hasStatus(422)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"TWIN_NOT_CONFIRMED\"}");
    }

    @Test
    void a_duplicate_changed_on_vinted_is_refused_with_its_differences() {
        twoDuplicates();
        vinted.showing(aListing(2, "Robe bleue", "10.00", ACTIVE));

        assertThat(mvc.post().uri("/api/listings/2/duplicate-deletion"))
                .hasStatus(422)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "LISTING_CHANGED", "differences": [{"kind": "PRICE_CHANGED",
                          "savedPrice": {"amount": 12.00, "currency": "EUR"},
                          "readPrice": {"amount": 10.00, "currency": "EUR"}}]}""");
    }

    @Test
    void a_browser_problem_is_a_bad_gateway() {
        twoDuplicates();
        vinted.failingBeforeAnyClick(() -> new BrowserUnavailable("Chrome not found"));

        assertThat(mvc.post().uri("/api/listings/2/duplicate-deletion"))
                .hasStatus(502)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"BROWSER_UNAVAILABLE\"}");
    }

    @Test
    void unknown_or_invalid_listing() {
        assertThat(mvc.post().uri("/api/listings/999/duplicate-deletion")).hasStatus(404);
        assertThat(mvc.post().uri("/api/listings/abc/duplicate-deletion")).hasStatus(400);
    }

    // AT-47-15
    @Test
    void deletion_requested_by_another_site_is_refused() {
        twoDuplicates();

        assertThat(mvc.post().uri("/api/listings/2/duplicate-deletion").header("Origin", "https://example.test"))
                .hasStatus(403);
        assertThat(vinted.opened()).isEmpty();
        assertThat(listings.findAll()).hasSize(2);
    }

    private void twoDuplicates() {
        for (long id = 1; id <= 2; id++) {
            ScannedListing listing = aListing(id, "Robe bleue", "12.00", ACTIVE);
            listings.saveAll(List.of(Listing.seen(listing, SCANNED_AT)));
            vinted.showing(listing);
        }
    }

    @TestConfiguration
    static class Fakes {

        @Bean
        InMemoryListingRepository listings() {
            return new InMemoryListingRepository();
        }

        @Bean
        FakeVintedPages vinted() {
            return new FakeVintedPages();
        }

        @Bean
        ShowDuplicates showDuplicates(InMemoryListingRepository listings) {
            return new ShowDuplicates(listings, new InMemoryListingBackupRepository(),
                    new InMemoryListingDeletionRepository(), new InMemoryListingRepublicationRepository(),
                    new InMemoryListingSaleRepository(), DeletionMode.REAL);
        }

        @Bean
        DeleteDuplicate deleteDuplicate(ShowDuplicates showDuplicates, InMemoryListingRepository listings,
                                        FakeVintedPages vinted) {
            Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
            InMemoryActionJournal journal = new InMemoryActionJournal();
            return new DeleteDuplicate(new VintedSession(), showDuplicates, listings, vinted,
                    new InMemoryListingEraser(listings, new InMemoryListingBackupRepository()),
                    new InMemoryPhotoStorage(), DeletionMode.REAL,
                    new CadenceCheck(new InMemoryListingDeletionRepository(), new InMemoryListingRepublicationRepository(),
                            journal, new WriteCadence(Duration.ofMinutes(2), Duration.ofMinutes(2), 20, 20), clock),
                    new JournalWriter(journal, clock));
        }
    }
}
