package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.application.journal.ShowActionJournal;
import fr.vinted.republisher.domain.journal.ActionOutcome;
import fr.vinted.republisher.domain.journal.ActionRecord;
import fr.vinted.republisher.domain.journal.JournaledAction;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static org.assertj.core.api.Assertions.assertThat;

/** US-23: the API answers as described by the OpenAPI contract. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(ActionJournalApiController.class)
class ActionJournalApiControllerTest {

    private static final Instant AT = Instant.parse("2026-11-15T10:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private InMemoryActionJournal journal;

    @Autowired
    private InMemoryListingRepository listings;

    @Test
    void shows_each_entry_with_its_listing_and_reason() {
        listings.saveAll(List.of(Listing.seen(aListing(101, "Pull rayé", "10", ACTIVE), AT)));
        journal.write(new ActionRecord(AT, JournaledAction.SCAN, ActionOutcome.SUCCEEDED, Optional.empty(),
                Optional.empty()));
        journal.write(new ActionRecord(AT.plusSeconds(60), JournaledAction.DELETION, ActionOutcome.REFUSED,
                Optional.of(new VintedListingId(101)), Optional.of("NOT_VALIDATED")));

        assertThat(mvc.get().uri("/api/journal"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"entries": [
                           {"entryId": 2, "occurredAt": "2026-11-15T10:01:00Z", "action": "DELETION",
                            "outcome": "REFUSED", "listingId": 101, "listingTitle": "Pull rayé",
                            "reason": "NOT_VALIDATED"},
                           {"entryId": 1, "occurredAt": "2026-11-15T10:00:00Z", "action": "SCAN",
                            "outcome": "SUCCEEDED"}],
                         "hasMore": false}""");
    }

    @Test
    void pages_of_fifty_most_recent_first() {
        // US-23 AT-23-11. Étant donné 120 lignes dans le journal
        IntStream.range(0, 120).forEach(rank -> journal.write(new ActionRecord(AT.plusSeconds(rank),
                JournaledAction.SCAN, ActionOutcome.STARTED, Optional.empty(), Optional.empty())));
        // Quand la page demande le journal, puis la suite avec before = dernière ligne reçue, deux fois
        // Alors elle reçoit 50 lignes (hasMore vrai), puis 50 (hasMore vrai), puis 20 (hasMore faux)
        assertThat(mvc.get().uri("/api/journal")).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.entries.length()").isEqualTo(50);
                    assertThat(json).extractingPath("$.entries[0].entryId").isEqualTo(120);
                    assertThat(json).extractingPath("$.hasMore").isEqualTo(true);
                });
        assertThat(mvc.get().uri("/api/journal?before=71")).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.entries.length()").isEqualTo(50);
                    assertThat(json).extractingPath("$.entries[0].entryId").isEqualTo(70);
                    assertThat(json).extractingPath("$.hasMore").isEqualTo(true);
                });
        assertThat(mvc.get().uri("/api/journal?before=21")).hasStatusOk().bodyJson()
                .satisfies(json -> {
                    assertThat(json).extractingPath("$.entries.length()").isEqualTo(20);
                    assertThat(json).extractingPath("$.entries[19].entryId").isEqualTo(1);
                    assertThat(json).extractingPath("$.hasMore").isEqualTo(false);
                });
    }

    @Test
    void before_outside_the_contract_is_refused() {
        assertThat(mvc.get().uri("/api/journal?before=0")).hasStatus(400);
        assertThat(mvc.get().uri("/api/journal?before=abc")).hasStatus(400);
    }

    @TestConfiguration
    static class JournalUseCase {

        @Bean
        InMemoryActionJournal journal() {
            return new InMemoryActionJournal();
        }

        @Bean
        InMemoryListingRepository listings() {
            return new InMemoryListingRepository();
        }

        @Bean
        ShowActionJournal showActionJournal(InMemoryActionJournal journal, InMemoryListingRepository listings) {
            return new ShowActionJournal(journal, listings);
        }
    }
}
