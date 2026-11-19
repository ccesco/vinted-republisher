package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.application.cadence.CadenceCheck;
import fr.vinted.republisher.domain.cadence.WriteCadence;
import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.application.republication.NewListingSearch;
import fr.vinted.republisher.application.republication.PrepareManualRepublication;
import fr.vinted.republisher.application.republication.RepublicationChecks;
import fr.vinted.republisher.application.republication.RepublishListing;
import fr.vinted.republisher.application.republication.ShowListingRepublication;
import fr.vinted.republisher.application.republication.ValidateListingRepublication;
import fr.vinted.republisher.application.republication.VerifyListingRepublication;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationMode;
import fr.vinted.republisher.domain.republication.RepublicationOutcome;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.FakeListingContentReader;
import fr.vinted.republisher.fakes.FakeListingPublisher;
import fr.vinted.republisher.fakes.FakeWardrobeReader;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static fr.vinted.republisher.domain.backup.BackupFixtures.noBackupPause;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.noPause;
import static org.assertj.core.api.Assertions.assertThat;

/** The API of a listing's republication answers as described by the OpenAPI contract. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(ListingRepublicationApiController.class)
class ListingRepublicationApiControllerTest {

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
    private InMemoryListingDeletionRepository deletions;

    @Autowired
    private InMemoryListingRepublicationRepository republications;

    @Autowired
    private FakeListingPublisher vinted;

    @Autowired
    private VintedSession session;

    @Autowired
    private FakeListingContentReader contents;

    @Test
    void published_republication_answers_with_its_new_listing() {
        aDeletedListing(Duration.ofMinutes(6));
        vinted.showingOncePublished(aListing(2, "Pull rayé 1", "12.50", ACTIVE));

        assertThat(republish("2026-11-10T10:00:00Z"))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"vintedId": 1, "republicationDryRun": false, "republication": {"outcome": "PUBLISHED",
                          "attemptedAt": "2026-11-11T10:00:00Z", "publishedAt": "2026-11-11T10:00:00Z",
                          "newListingId": 2, "newListingUrl": "https://vinted.test/items/2",
                          "backupSavedAt": "2026-11-10T10:00:00Z"}}""");
        assertThat(vinted.publishClicks()).isEqualTo(1);
    }

    @Test
    void cadence_limit_is_a_conflict_with_the_date_it_ends() {
        aDeletedListing(Duration.ofMinutes(6));
        republications.save(ListingRepublication.attempted(aBackup(5, NOW), NOW.minusSeconds(60)));

        assertThat(republish("2026-11-10T10:00:00Z"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "CADENCE_LIMIT_REACHED", "allowedAt": "2026-11-11T10:01:00Z"}""");
    }

    @Test
    void republication_left_in_error_answers_ok_with_its_outcome() {
        aDeletedListing(Duration.ofMinutes(6));

        assertThat(republish("2026-11-10T10:00:00Z"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.republication.outcome").isEqualTo("FAILED");
    }

    @Test
    void too_early_is_a_conflict_with_the_republication_date() {
        aDeletedListing(Duration.ofMinutes(2));

        assertThat(republish("2026-11-10T10:00:00Z"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "TOO_EARLY", "republishableAt": "2026-11-11T10:03:00Z"}""");
    }

    @Test
    void listing_not_deleted_is_a_conflict() {
        aValidatedListing();

        assertThat(republish("2026-11-10T10:00:00Z"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"NOT_DELETED\"}");
    }

    @Test
    void republication_in_error_is_a_conflict() {
        ListingBackup backup = aDeletedListing(Duration.ofMinutes(6));
        republications.save(ListingRepublication.attempted(backup, NOW));

        assertThat(republish("2026-11-10T10:00:00Z"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"REPUBLICATION_IN_ERROR\"}");
    }

    @Test
    void another_browser_action_is_a_conflict() {
        aDeletedListing(Duration.ofMinutes(6));
        session.start(BrowserAction.SCANNING);

        assertThat(republish("2026-11-10T10:00:00Z"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"BROWSER_BUSY\"}");
    }

    @Test
    void backup_not_validated_is_a_conflict() {
        aDeletedListing(Duration.ofMinutes(6));
        validations.save(new BackupValidation(new VintedListingId(1), SAVED_AT.minusSeconds(60), NOW));

        assertThat(republish("2026-11-10T10:00:00Z"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"NOT_VALIDATED\"}");
    }

    @Test
    void listing_already_republished_is_a_conflict() {
        ListingBackup backup = aDeletedListing(Duration.ofMinutes(6));
        listings.saveAll(List.of(Listing.seen(aListing(2, "Pull rayé 1", "12.50", ACTIVE), NOW)));
        republications.save(ListingRepublication.attempted(backup, NOW).published(new VintedListingId(2), NOW));

        assertThat(republish("2026-11-10T10:00:00Z"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"ALREADY_REPUBLISHED\"}");
    }

    @Test
    void unavailable_browser_is_a_bad_gateway() {
        aDeletedListing(Duration.ofMinutes(6));
        vinted.whereBrowserCannotStart();

        assertThat(republish("2026-11-10T10:00:00Z"))
                .hasStatus(502)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"BROWSER_UNAVAILABLE\"}");
    }

    @Test
    void vinted_unreachable_is_a_bad_gateway() {
        aDeletedListing(Duration.ofMinutes(6));
        vinted.whereVintedRefuses();

        assertThat(republish("2026-11-10T10:00:00Z"))
                .hasStatus(502)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"VINTED_UNREACHABLE\"}");
    }

    @Test
    void disconnected_session_is_a_bad_gateway() {
        aDeletedListing(Duration.ofMinutes(6));
        vinted.whereSessionIsDisconnected();

        assertThat(republish("2026-11-10T10:00:00Z"))
                .hasStatus(502)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"SESSION_DISCONNECTED\"}");
    }

    @Test
    void replaced_backup_is_a_conflict() {
        aDeletedListing(Duration.ofMinutes(6));

        assertThat(republish("2026-11-09T10:00:00Z"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"BACKUP_REPLACED\"}");
    }

    @Test
    void missing_photo_is_refused_with_the_verification() {
        ListingBackup backup = aDeletedListing(Duration.ofMinutes(6));
        photos.lose(1, backup.photos().getFirst());

        assertThat(republish("2026-11-10T10:00:00Z"))
                .hasStatus(422)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "PHOTOS_NOT_VERIFIED",
                         "verification": {"passed": false, "failures": [{"kind": "PHOTO_MISSING", "rank": 0}]}}""");
    }

    @Test
    void field_not_fillable_names_the_field_and_its_option() {
        aDeletedListing(Duration.ofMinutes(6));
        vinted.whereSizeCannotBeFilled();

        assertThat(republish("2026-11-10T10:00:00Z"))
                .hasStatus(422)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "FIELD_NOT_FILLABLE", "field": "SIZE", "optionId": 588}""");
    }

    @Test
    void unreadable_form_is_a_bad_gateway() {
        aDeletedListing(Duration.ofMinutes(6));
        vinted.whereTheFormIsUnreadable();

        assertThat(republish("2026-11-10T10:00:00Z"))
                .hasStatus(502)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"FORM_UNREADABLE\"}");
    }

    @Test
    void unexpected_failure_is_a_failed_republication() {
        aDeletedListing(Duration.ofMinutes(6));
        vinted.whereBrowserFailsUnexpectedly();

        assertThat(republish("2026-11-10T10:00:00Z"))
                .hasStatus(500)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"REPUBLICATION_FAILED\"}");
    }

    @Test
    void listing_without_backup_is_not_found() {
        assertThat(republish("2026-11-10T10:00:00Z")).hasStatus(404);
    }

    @Test
    void request_without_the_date_seen_is_invalid() {
        aDeletedListing(Duration.ofMinutes(6));

        assertThat(mvc.post().uri("/api/listings/1/republication").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).hasStatus(400);
        assertThat(mvc.post().uri("/api/listings/1/republication").contentType(MediaType.APPLICATION_JSON)
                .content("{\"savedAt\": \"not a date\"}")).hasStatus(400);
        assertThat(vinted.formsOpened()).isZero();
    }

    // AT-12-17
    @Test
    void refusesCrossSiteRequest() {
        aDeletedListing(Duration.ofMinutes(6));

        assertThat(mvc.post().uri("/api/listings/1/republication").header("Origin", "https://example.test")
                .contentType(MediaType.APPLICATION_JSON).content("{\"savedAt\": \"2026-11-10T10:00:00Z\"}"))
                .hasStatus(403);
        assertThat(vinted.formsOpened()).isZero();
    }

    @Test
    void failed_republication_missing_from_the_newest_listings_stays_failed() {
        ListingBackup backup = aDeletedListing(Duration.ofMinutes(6));
        republications.save(ListingRepublication.attempted(backup, NOW));

        assertThat(verify())
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"vintedId": 1, "republication": {"outcome": "FAILED", "attemptedAt": "2026-11-11T10:00:00Z"}}""");
    }

    @Test
    void republication_validated_published_answers_with_its_new_listing_and_comparison() {
        ListingBackup backup = aDeletedListing(Duration.ofMinutes(6));
        republications.save(ListingRepublication.attempted(backup, NOW));
        contents.showing(new ListingReading(aContent(2, "Pull rayé 1", "Porté deux fois", "12.50"), List.of(aPhoto("a"))));

        assertThat(validate("{\"published\": true, \"newListingUrl\": \"https://www.vinted.fr/items/2-pull?ref=x\"}"))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"vintedId": 1, "republication": {"outcome": "PUBLISHED", "newListingId": 2,
                          "publishedAt": "2026-11-11T10:00:00Z"}, "republicationComparison": []}""");
    }

    @Test
    void republication_validated_not_published_answers_with_its_outcome() {
        ListingBackup backup = aDeletedListing(Duration.ofMinutes(6));
        republications.save(ListingRepublication.attempted(backup, NOW));

        assertThat(validate("{\"published\": false}"))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"vintedId": 1, "republication": {"outcome": "NOT_PUBLISHED",
                          "checkedAt": "2026-11-11T10:00:00Z"}}""");
    }

    @Test
    void invalid_new_listing_link_is_a_bad_request() {
        ListingBackup backup = aDeletedListing(Duration.ofMinutes(6));
        republications.save(ListingRepublication.attempted(backup, NOW));

        assertThat(validate("{\"published\": true, \"newListingUrl\": \"https://www.vinted.fr/member/42\"}"))
                .hasStatus(400)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"NEW_LISTING_LINK_INVALID\"}");
        assertThat(validate("{\"published\": true}"))
                .hasStatus(400)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"NEW_LISTING_LINK_INVALID\"}");
        assertThat(validate("{}")).hasStatus(400);
        assertThat(republications.findLatest(new VintedListingId(1))).map(ListingRepublication::outcome)
                .contains(RepublicationOutcome.FAILED);
    }

    @Test
    void new_listing_with_another_title_or_price_is_a_conflict() {
        ListingBackup backup = aDeletedListing(Duration.ofMinutes(6));
        republications.save(ListingRepublication.attempted(backup, NOW));
        contents.showing(new ListingReading(aContent(2, "Pull rayé 1", "Porté deux fois", "15.00"), List.of(aPhoto("a"))));

        assertThat(validate("{\"published\": true, \"newListingUrl\": \"https://www.vinted.fr/items/2-pull\"}"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"NEW_LISTING_MISMATCH\"}");
    }

    // AT-13-21
    @Test
    void refusesCrossSiteValidation() {
        ListingBackup backup = aDeletedListing(Duration.ofMinutes(6));
        republications.save(ListingRepublication.attempted(backup, NOW));

        assertThat(validate("{\"published\": false}").header("Origin", "https://example.test"))
                .hasStatus(403);
        assertThat(republications.findLatest(new VintedListingId(1))).map(ListingRepublication::outcome)
                .contains(RepublicationOutcome.FAILED);
    }

    @Test
    void published_republication_answers_with_its_comparison() {
        ListingBackup backup = aDeletedListing(Duration.ofMinutes(6));
        listings.saveAll(List.of(Listing.seen(aListing(2, "Pull rayé 1", "12.50", ACTIVE), NOW)));
        republications.save(ListingRepublication.attempted(backup, NOW).published(new VintedListingId(2), NOW));
        contents.showing(new ListingReading(aContent(2), List.of(aPhoto("a"), aPhoto("b"))));

        assertThat(verify())
                .hasStatusOk()
                .bodyJson().extractingPath("$.republicationComparison").isEqualTo(List.of(
                        Map.of("field", "PHOTOS", "saved", "1", "read", "2"),
                        Map.of("field", "TITLE", "saved", "Pull rayé 1", "read", "Pull rayé 2")));
    }

    @Test
    void nothing_to_verify_is_a_conflict() {
        aDeletedListing(Duration.ofMinutes(6));

        assertThat(verify())
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"NO_REPUBLICATION_TO_VERIFY\"}");
    }

    @Test
    void unreadable_new_listing_is_a_bad_gateway() {
        ListingBackup backup = aDeletedListing(Duration.ofMinutes(6));
        listings.saveAll(List.of(Listing.seen(aListing(2, "Pull rayé 1", "12.50", ACTIVE), NOW)));
        republications.save(ListingRepublication.attempted(backup, NOW).published(new VintedListingId(2), NOW));
        contents.whereFormIsUnreadable(2, "no title");

        assertThat(verify())
                .hasStatus(502)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"LISTING_UNREADABLE\"}");
    }

    // AT-13-12
    @Test
    void refusesCrossSiteVerification() {
        ListingBackup backup = aDeletedListing(Duration.ofMinutes(6));
        republications.save(ListingRepublication.attempted(backup, NOW));

        assertThat(mvc.post().uri("/api/listings/1/republication/verification")
                .header("Origin", "https://example.test"))
                .hasStatus(403);
        assertThat(republications.findLatest(new VintedListingId(1))).map(ListingRepublication::outcome)
                .contains(RepublicationOutcome.FAILED);
    }

    @Test
    void manual_republication_answers_with_the_fields_left_empty() {
        // AT-45-06. Quand le membre demande la republication manuelle d'une annonce
        // Alors l'API répond selon le contrat (200, 409, 502, 500)
        aDeletedListing(Duration.ofMinutes(6));
        vinted.whereSizeCannotBeFilled();

        assertThat(prepareManually("2026-11-10T10:00:00Z"))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"listing": {"vintedId": 1, "republication": {"outcome": "FAILED",
                                     "attemptedAt": "2026-11-11T10:00:00Z"}},
                         "unfilledFields": [{"field": "SIZE", "optionId": 588}]}""");
        assertThat(vinted.publishClicks()).isZero();
    }

    @Test
    void manual_republication_waits_for_no_republication_date() {
        aDeletedListing(Duration.ofMinutes(2));

        assertThat(prepareManually("2026-11-10T10:00:00Z"))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"listing": {"vintedId": 1, "republication": {"outcome": "FAILED",
                                     "attemptedAt": "2026-11-11T10:00:00Z"}},
                         "unfilledFields": []}""");
    }

    @Test
    void manual_republication_with_disconnected_session_is_a_bad_gateway() {
        aDeletedListing(Duration.ofMinutes(6));
        vinted.whereSessionIsDisconnected();

        assertThat(prepareManually("2026-11-10T10:00:00Z"))
                .hasStatus(502)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "SESSION_DISCONNECTED"}""");
        assertThat(republications.findAll()).isEmpty();
    }

    @Test
    void manual_republication_unexpected_failure_is_a_failed_republication() {
        aDeletedListing(Duration.ofMinutes(6));
        vinted.whereBrowserFailsUnexpectedly();

        assertThat(prepareManually("2026-11-10T10:00:00Z"))
                .hasStatus(500)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "REPUBLICATION_FAILED"}""");
    }

    private MockMvcTester.MockMvcRequestBuilder prepareManually(String savedAt) {
        return mvc.post().uri("/api/listings/1/republication/manual").contentType(MediaType.APPLICATION_JSON)
                .content("{\"savedAt\": \"" + savedAt + "\"}");
    }

    private MockMvcTester.MockMvcRequestBuilder validate(String validation) {
        return mvc.post().uri("/api/listings/1/republication/manual-validation").contentType(MediaType.APPLICATION_JSON)
                .content(validation);
    }

    private MockMvcTester.MockMvcRequestBuilder verify() {
        return mvc.post().uri("/api/listings/1/republication/verification");
    }

    private MockMvcTester.MockMvcRequestBuilder republish(String savedAt) {
        return mvc.post().uri("/api/listings/1/republication").contentType(MediaType.APPLICATION_JSON)
                .content("{\"savedAt\": \"" + savedAt + "\"}");
    }

    private ListingBackup aValidatedListing() {
        ListingReading reading = new ListingReading(aContent(1), List.of(aPhoto("front")));
        listings.saveAll(List.of(Listing.seen(aListing(1, "Pull rayé 1", "12.50", ACTIVE), SAVED_AT).absent()));
        reading.photos().forEach(photo -> photos.store(reading.content().id(), photo));
        ListingBackup backup = ListingBackup.of(reading, SAVED_AT);
        listingBackups.save(backup);
        validations.save(BackupValidation.of(backup, SAVED_AT));
        return backup;
    }

    private ListingBackup aDeletedListing(Duration ago) {
        ListingBackup backup = aValidatedListing();
        deletions.save(ListingDeletion.attempted(backup, NOW.minus(ago)).confirmed(NOW.minus(ago)));
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
        InMemoryListingRepublicationRepository republications() {
            return new InMemoryListingRepublicationRepository();
        }

        @Bean
        FakeWardrobeReader wardrobe() {
            return new FakeWardrobeReader();
        }

        @Bean
        FakeListingPublisher vinted(FakeWardrobeReader wardrobe) {
            return new FakeListingPublisher(wardrobe);
        }

        @Bean
        VintedSession session() {
            return new VintedSession();
        }

        @Bean
        ShowListingRepublication showListingRepublication(InMemoryListingRepository listings,
                                                          InMemoryListingBackupRepository listingBackups,
                                                          InMemoryPhotoStorage photos,
                                                          InMemoryBackupValidationRepository validations,
                                                          InMemoryListingDeletionRepository deletions,
                                                          InMemoryListingRepublicationRepository republications) {
            return new ShowListingRepublication(new ShowListingDeletion(listings, listingBackups, photos, validations,
                    deletions, DeletionMode.DRY_RUN, new RepublicationDelay(Duration.ofMinutes(5))),
                    republications, listings, RepublicationMode.REAL);
        }

        @Bean
        RepublishListing republishListing(VintedSession session, InMemoryListingRepository listings,
                                          InMemoryListingBackupRepository listingBackups, InMemoryPhotoStorage photos,
                                          InMemoryBackupValidationRepository validations,
                                          InMemoryListingDeletionRepository deletions,
                                          InMemoryListingRepublicationRepository republications,
                                          FakeListingPublisher vinted, FakeWardrobeReader wardrobe,
                                          ShowListingRepublication show) {
            Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
            return new RepublishListing(session, new RepublicationChecks(listingBackups, photos, validations,
                    deletions, republications, new RepublicationDelay(Duration.ofMinutes(5)), clock),
                    new CadenceCheck(deletions, republications, new WriteCadence(Duration.ofMinutes(2), Duration.ofMinutes(2), 20, 20), clock),
                    republications, vinted, new NewListingSearch(wardrobe, noPause(), listings), listings,
                    RepublicationMode.REAL, clock, show, new JournalWriter(new InMemoryActionJournal(), Clock.systemUTC()), (instant, interrupted) -> { }, Duration.ZERO);
        }
        @Bean
        PrepareManualRepublication prepareManualRepublication(VintedSession session,
                                                              InMemoryListingBackupRepository listingBackups,
                                                              InMemoryPhotoStorage photos,
                                                              InMemoryBackupValidationRepository validations,
                                                              InMemoryListingDeletionRepository deletions,
                                                              InMemoryListingRepublicationRepository republications,
                                                              FakeListingPublisher vinted, ShowListingRepublication show) {
            Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
            return new PrepareManualRepublication(session, new RepublicationChecks(listingBackups, photos,
                    validations, deletions, republications, new RepublicationDelay(Duration.ofMinutes(5)), clock),
                    republications, vinted, RepublicationMode.REAL, clock, show,
                    new JournalWriter(new InMemoryActionJournal(), clock));
        }

        @Bean
        FakeListingContentReader contents() {
            return new FakeListingContentReader();
        }

        @Bean
        ValidateListingRepublication validateListingRepublication(VintedSession session,
                                                                  InMemoryListingBackupRepository listingBackups,
                                                                  InMemoryListingRepublicationRepository republications,
                                                                  InMemoryListingRepository listings,
                                                                  FakeListingContentReader contents,
                                                                  ShowListingRepublication show) {
            return new ValidateListingRepublication(session, listingBackups, republications, listings, contents,
                    noBackupPause(), Clock.fixed(NOW, ZoneOffset.UTC), show,
                    URI.create("https://www.vinted.fr/member/42"),
                    new JournalWriter(new InMemoryActionJournal(), Clock.systemUTC()));
        }

        @Bean
        VerifyListingRepublication verifyListingRepublication(VintedSession session,
                                                              InMemoryListingBackupRepository listingBackups,
                                                              InMemoryListingRepublicationRepository republications,
                                                              InMemoryListingRepository listings,
                                                              FakeWardrobeReader wardrobe,
                                                              FakeListingContentReader contents,
                                                              ShowListingRepublication show) {
            return new VerifyListingRepublication(session, listingBackups, republications, listings,
                    new NewListingSearch(wardrobe, noPause(), listings), contents, noBackupPause(),
                    Clock.fixed(NOW, ZoneOffset.UTC), show,
                    new JournalWriter(new InMemoryActionJournal(), Clock.systemUTC()));
        }
    }
}
