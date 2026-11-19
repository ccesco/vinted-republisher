package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.application.inventory.ShowFailedListings;
import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryBackupVerificationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import fr.vinted.republisher.fakes.InMemoryListingTagRepository;
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
import java.util.List;
import java.util.Optional;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;

/** US-39: the API answers as described by the OpenAPI contract. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(FailedListingsApiController.class)
class FailedListingsApiControllerTest {

    private static final Instant AT = Instant.parse("2026-11-18T10:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private InMemoryActionJournal journal;

    @Autowired
    private InMemoryListingRepository listings;

    @Test
    void shows_each_failed_listing_with_its_stage_and_cause() {
        // AT-39-05. Quand le membre demande GET /api/failed-listings
        // Alors l'API renvoie chaque annonce en échec avec titre, étape et ligne du journal
        // AT-45-06. Et GET /api/failed-listings donne le groupe de chaque annonce
        listings.saveAll(List.of(Listing.seen(aListing(101, "Pull bleu", "10", ACTIVE), AT)));
        journal.write(new ActionRecord(AT, JournaledAction.BACKUP_VALIDATION, ActionOutcome.REFUSED,
                Optional.of(new VintedListingId(101)), Optional.of("BACKUP_REPLACED")));

        assertThat(mvc.get().uri("/api/failed-listings"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"listings": [
                           {"listingId": 101, "listingTitle": "Pull bleu", "stage": "TO_BACK_UP", "kind": "REFUSED",
                            "failure": {"entryId": 1, "occurredAt": "2026-11-18T10:00:00Z",
                                        "action": "BACKUP_VALIDATION", "outcome": "REFUSED", "listingId": 101,
                                        "listingTitle": "Pull bleu", "reason": "BACKUP_REPLACED"}}]}""");
    }

    @Test
    void no_failed_listing_gives_an_empty_list() {
        assertThat(mvc.get().uri("/api/failed-listings"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"listings": []}""");
    }

    @TestConfiguration
    static class FailedListingsUseCase {

        @Bean
        InMemoryActionJournal journal() {
            return new InMemoryActionJournal();
        }

        @Bean
        InMemoryListingRepository listings() {
            return new InMemoryListingRepository();
        }

        @Bean
        ShowFailedListings showFailedListings(InMemoryActionJournal journal, InMemoryListingRepository listings) {
            return new ShowFailedListings(journal, new ShowListingInventory(listings,
                    new InMemoryListingStatisticsRepository(), new InMemoryListingBackupRepository(),
                    new InMemoryBackupValidationRepository(), new InMemoryListingDeletionRepository(),
                    new InMemoryListingRepublicationRepository(), new RepublicationDelay(Duration.ofMinutes(5)),
                    Clock.systemUTC(), new InMemoryListingTagRepository(),
                    new InMemoryBackupVerificationRepository()));
        }
    }
}
