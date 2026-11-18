package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.application.cadence.CadenceCheck;
import fr.vinted.republisher.domain.cadence.WriteCadence;
import fr.vinted.republisher.application.deletion.DeleteListing;
import fr.vinted.republisher.application.deletion.RecheckListingDeletion;
import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.application.republication.ShowListingRepublication;
import fr.vinted.republisher.domain.republication.RepublicationMode;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.FakeListingDeleter;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;

/** The API of a listing's deletion answers as described by the OpenAPI contract. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(ListingDeletionApiController.class)
class ListingDeletionApiControllerTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private InMemoryListingRepository listings;

    @Autowired
    private InMemoryListingBackupRepository listingBackups;

    @Autowired
    private InMemoryPhotoStorage photos;

    @Autowired
    private InMemoryBackupValidationRepository validations;

    @Autowired
    private FakeListingDeleter vinted;

    @Autowired
    private InMemoryListingDeletionRepository deletions;

    @Autowired
    private VintedSession session;

    @Test
    void dry_run_answers_that_nothing_was_deleted() {
        aValidatedListing();

        assertThat(delete("2026-11-10T10:00:00Z"))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"vintedId": 1, "deletionDryRun": true, "dryRunPassed": true}""");
        assertThat(vinted.confirmationClicks()).isZero();
    }

    @Test
    void listing_changed_on_vinted_is_refused_with_its_differences() {
        aValidatedListing();
        vinted.showing(aListing(1, "Pull rayé 1", "10.00", ACTIVE));

        assertThat(delete("2026-11-10T10:00:00Z"))
                .hasStatus(422)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "LISTING_CHANGED", "differences": [{"kind": "PRICE_CHANGED",
                          "savedPrice": {"amount": 12.50, "currency": "EUR"},
                          "readPrice": {"amount": 10.00, "currency": "EUR"}}]}""");
    }

    @Test
    void failed_verification_is_refused_with_its_failures() {
        ListingBackup backup = aValidatedListing();
        photos.lose(1, backup.photos().getFirst());

        assertThat(delete("2026-11-10T10:00:00Z"))
                .hasStatus(422)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "VERIFICATION_FAILED",
                         "verification": {"passed": false, "failures": [{"kind": "PHOTO_MISSING", "rank": 0}]}}""");
    }

    @Test
    void backup_not_validated_is_a_conflict() {
        aValidatedListing();
        validations.save(new BackupValidation(new VintedListingId(1), SAVED_AT.minusSeconds(60), NOW));

        assertThat(delete("2026-11-10T10:00:00Z"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"NOT_VALIDATED\"}");
    }

    @Test
    void replaced_backup_is_a_conflict() {
        aValidatedListing();

        assertThat(delete("2026-11-09T10:00:00Z"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"BACKUP_REPLACED\"}");
    }

    @Test
    void vinted_unreachable_is_a_bad_gateway() {
        aValidatedListing();
        vinted.whereVintedRefuses();

        assertThat(delete("2026-11-10T10:00:00Z"))
                .hasStatus(502)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"VINTED_UNREACHABLE\"}");
    }

    @Test
    void missing_delete_button_is_a_bad_gateway() {
        aValidatedListing();
        vinted.whereTheDeleteButtonIsMissing();

        assertThat(delete("2026-11-10T10:00:00Z"))
                .hasStatus(502)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"LISTING_PAGE_UNREADABLE\"}");
    }

    @Test
    void listing_without_backup_is_not_found() {
        assertThat(delete("2026-11-10T10:00:00Z")).hasStatus(404);
    }

    @Test
    void request_without_the_date_seen_is_invalid() {
        aValidatedListing();

        assertThat(mvc.post().uri("/api/listings/1/deletion").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .hasStatus(400);
    }

    @Test
    void recheck_answers_with_the_settled_deletion() {
        ListingBackup backup = aValidatedListing();
        deletions.save(ListingDeletion.attempted(backup, NOW));
        vinted.showingNothing();

        assertThat(mvc.post().uri("/api/listings/1/deletion/recheck"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.deletion").isEqualTo(Map.of("outcome", "CONFIRMED",
                        "attemptedAt", "2026-11-11T10:00:00Z", "settledAt", "2026-11-11T10:00:00Z",
                        "backupSavedAt", "2026-11-10T10:00:00Z", "republishableAt", "2026-11-11T10:05:00Z"));
    }

    @Test
    void deletion_in_doubt_is_a_conflict() {
        ListingBackup backup = aValidatedListing();
        deletions.save(ListingDeletion.attempted(backup, NOW));

        assertThat(delete("2026-11-10T10:00:00Z"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"DELETION_UNCONFIRMED\"}");
    }

    @Test
    void listing_already_deleted_is_a_conflict() {
        ListingBackup backup = aValidatedListing();
        deletions.save(ListingDeletion.attempted(backup, NOW).confirmed(NOW));

        assertThat(delete("2026-11-10T10:00:00Z"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"ALREADY_DELETED\"}");
    }

    @Test
    void another_browser_action_is_a_conflict() {
        aValidatedListing();
        session.start(BrowserAction.SCANNING);

        assertThat(delete("2026-11-10T10:00:00Z"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"BROWSER_BUSY\"}");
    }

    @Test
    void cadence_limit_is_a_conflict_with_the_date_it_ends() {
        aValidatedListing();
        vinted.whereCadenceLimitIsReachedUntil(NOW.plusSeconds(60));

        assertThat(delete("2026-11-10T10:00:00Z"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "CADENCE_LIMIT_REACHED", "allowedAt": "2026-11-11T10:01:00Z"}""");
    }

    @Test
    void disconnected_session_is_a_bad_gateway() {
        aValidatedListing();
        vinted.whereSessionIsDisconnected();

        assertThat(delete("2026-11-10T10:00:00Z"))
                .hasStatus(502)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"SESSION_DISCONNECTED\"}");
    }

    @Test
    void unavailable_browser_is_a_bad_gateway() {
        aValidatedListing();
        vinted.whereBrowserCannotStart();

        assertThat(delete("2026-11-10T10:00:00Z"))
                .hasStatus(502)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"BROWSER_UNAVAILABLE\"}");
    }

    @Test
    void unexpected_failure_is_a_failed_deletion() {
        aValidatedListing();
        vinted.whereBrowserFailsUnexpectedly();

        assertThat(delete("2026-11-10T10:00:00Z"))
                .hasStatus(500)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"DELETION_FAILED\"}");
    }

    @Test
    void malformed_request_is_invalid() {
        aValidatedListing();

        assertThat(mvc.post().uri("/api/listings/1/deletion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"savedAt\": \"not a date\"}")).hasStatus(400);
        assertThat(mvc.post().uri("/api/listings/abc/deletion/recheck")).hasStatus(400);
        assertThat(vinted.pagesOpened()).isZero();
    }

    @Test
    void recheck_without_unconfirmed_deletion_is_a_conflict() {
        aValidatedListing();

        assertThat(mvc.post().uri("/api/listings/1/deletion/recheck"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"NO_UNCONFIRMED_DELETION\"}");
    }

    @Test
    void deletion_requested_by_another_site_is_refused() {
        aValidatedListing();

        assertThat(mvc.post().uri("/api/listings/1/deletion").header("Origin", "https://example.test")
                .contentType(MediaType.APPLICATION_JSON).content("{\"savedAt\": \"2026-11-10T10:00:00Z\"}"))
                .hasStatus(403);
        assertThat(mvc.post().uri("/api/listings/1/deletion/recheck").header("Origin", "https://example.test"))
                .hasStatus(403);
        assertThat(vinted.pagesOpened()).isZero();
    }

    private MockMvcTester.MockMvcRequestBuilder delete(String savedAt) {
        return mvc.post().uri("/api/listings/1/deletion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"savedAt\": \"" + savedAt + "\"}");
    }

    private ListingBackup aValidatedListing() {
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), SCANNED_AT)));
        ListingReading reading = new ListingReading(aContent(1), List.of(aPhoto("front")));
        reading.photos().forEach(photo -> photos.store(reading.content().id(), photo));
        ListingBackup backup = ListingBackup.of(reading, SAVED_AT);
        listingBackups.save(backup);
        validations.save(BackupValidation.of(backup, SCANNED_AT));
        vinted.showing(anActiveListing(1));
        return backup;
    }

    @TestConfiguration
    static class Fakes {

        @Bean
        InMemoryListingRepository listings() {
            return new InMemoryListingRepository();
        }

        @Bean
        InMemoryListingBackupRepository listingBackups() {
            return new InMemoryListingBackupRepository();
        }

        @Bean
        InMemoryPhotoStorage photos() {
            return new InMemoryPhotoStorage();
        }

        @Bean
        InMemoryBackupValidationRepository validations() {
            return new InMemoryBackupValidationRepository();
        }

        @Bean
        InMemoryListingDeletionRepository deletions() {
            return new InMemoryListingDeletionRepository();
        }

        @Bean
        FakeListingDeleter vinted() {
            return new FakeListingDeleter();
        }

        @Bean
        VintedSession session() {
            return new VintedSession();
        }

        @Bean
        DeleteListing deleteListing(VintedSession session, InMemoryListingRepository listings,
                                    InMemoryListingBackupRepository listingBackups, InMemoryPhotoStorage photos,
                                    InMemoryBackupValidationRepository validations,
                                    InMemoryListingDeletionRepository deletions, FakeListingDeleter vinted) {
            return new DeleteListing(session, listings, listingBackups, photos, validations, deletions, vinted,
                    DeletionMode.DRY_RUN,
                    new RepublicationDelay(Duration.ofMinutes(5)),
                    new CadenceCheck(deletions, new InMemoryListingRepublicationRepository(),
                            new WriteCadence(Duration.ofMinutes(2), Duration.ofMinutes(2), 20, 20), Clock.fixed(NOW, ZoneOffset.UTC)),
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    new JournalWriter(new InMemoryActionJournal(), Clock.systemUTC()));
        }

        @Bean
        ShowListingRepublication showListingRepublication(InMemoryListingRepository listings,
                                                          InMemoryListingBackupRepository listingBackups,
                                                          InMemoryPhotoStorage photos,
                                                          InMemoryBackupValidationRepository validations,
                                                          InMemoryListingDeletionRepository deletions) {
            return new ShowListingRepublication(new ShowListingDeletion(listings, listingBackups, photos, validations,
                    deletions, DeletionMode.DRY_RUN, new RepublicationDelay(Duration.ofMinutes(5))),
                    new InMemoryListingRepublicationRepository(), listings, RepublicationMode.DRY_RUN);
        }

        @Bean
        RecheckListingDeletion recheckListingDeletion(VintedSession session, InMemoryListingRepository listings,
                                                      InMemoryListingBackupRepository listingBackups,
                                                      InMemoryPhotoStorage photos,
                                                      InMemoryBackupValidationRepository validations,
                                                      InMemoryListingDeletionRepository deletions,
                                                      FakeListingDeleter vinted) {
            return new RecheckListingDeletion(session, listings, listingBackups, photos, validations, deletions,
                    vinted, DeletionMode.DRY_RUN,
                    new RepublicationDelay(Duration.ofMinutes(5)), Clock.fixed(NOW, ZoneOffset.UTC),
                    new JournalWriter(new InMemoryActionJournal(), Clock.systemUTC()));
        }
    }
}
