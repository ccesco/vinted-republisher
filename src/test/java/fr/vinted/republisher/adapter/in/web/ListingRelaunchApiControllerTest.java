package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.application.cadence.CadenceCheck;
import fr.vinted.republisher.application.deletion.DeleteListing;
import fr.vinted.republisher.application.deletion.RecheckListingDeletion;
import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.application.inventory.ShowListingDetail;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.application.relaunch.RelaunchListing;
import fr.vinted.republisher.application.republication.NewListingSearch;
import fr.vinted.republisher.application.republication.RepublicationChecks;
import fr.vinted.republisher.application.republication.RepublishListing;
import fr.vinted.republisher.application.republication.ShowListingRepublication;
import fr.vinted.republisher.application.republication.VerifyListingRepublication;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.cadence.WriteCadence;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.deletion.ListingDeletion;
import fr.vinted.republisher.domain.republication.ListingRepublication;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationMode;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ScannedListing;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.InMemoryListingSaleRepository;
import fr.vinted.republisher.fakes.FakeListingContentReader;
import fr.vinted.republisher.fakes.FakeListingDeleter;
import fr.vinted.republisher.fakes.FakeListingPublisher;
import fr.vinted.republisher.fakes.FakeWardrobeReader;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryBackupVerificationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
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
import java.time.Instant;
import java.util.List;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static fr.vinted.republisher.domain.backup.BackupFixtures.noBackupPause;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.noPause;
import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(ListingRelaunchApiController.class)
class ListingRelaunchApiControllerTest {

    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");
    private static final ScannedListing ON_VINTED = aListing(1, "Pull rayé 1", "12.50", ACTIVE);

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
    private FakeListingDeleter deleter;

    @Autowired
    private FakeWardrobeReader wardrobe;

    @Autowired
    private VintedSession session;

    @Test
    void relaunch_answers_the_action_and_the_listing_read_again() {
        aValidatedListing();
        deleter.showing(ON_VINTED);

        assertThat(relaunch("TO_DELETE"))
                .hasStatusOk()
                .bodyJson().satisfies(body -> {
                    assertThat(body).extractingPath("$.action").isEqualTo("DELETION");
                    assertThat(body).extractingPath("$.dryRun").isEqualTo(false);
                    assertThat(body).extractingPath("$.listing.vintedId").isEqualTo(1);
                    assertThat(body).extractingPath("$.listing.stage").isEqualTo("WAITING");
                });
    }

    @Test
    void unknown_listing_is_not_found() {
        assertThat(relaunch("TO_DELETE")).hasStatus(404);
    }

    @Test
    void changed_stage_is_a_conflict_with_the_current_stage() {
        aValidatedListing();

        assertThat(relaunch("TO_REPUBLISH"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "STAGE_CHANGED", "stage": "TO_DELETE"}""");
    }

    @Test
    void waiting_listing_is_not_relaunchable_until_its_republication_date() {
        ListingBackup backup = aValidatedListing();
        deletions.save(ListingDeletion.attempted(backup, NOW.minusSeconds(65)).confirmed(NOW.minusSeconds(60)));

        assertThat(relaunch("WAITING"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "NOT_RELAUNCHABLE", "stage": "WAITING", "allowedAt": "2026-11-11T10:04:00Z"}""");
    }

    @Test
    void cadence_limit_is_a_conflict_with_the_date_it_ends() {
        aValidatedListing();
        deleter.showing(ON_VINTED);
        deletions.save(ListingDeletion.attempted(aBackup(5, SAVED_AT), NOW.minusSeconds(60)));

        assertThat(relaunch("TO_DELETE"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "CADENCE_LIMIT_REACHED", "allowedAt": "2026-11-11T10:01:00Z"}""");
    }

    @Test
    void busy_browser_is_a_conflict() {
        aValidatedListing();
        session.start(BrowserAction.SCANNING);

        assertThat(relaunch("TO_DELETE"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "BROWSER_BUSY"}""");
    }

    @Test
    void refusal_of_the_launched_use_case_is_a_conflict() {
        aValidatedListing();
        deleter.showing(aListing(1, "Pull rayé 1", "15.00", ACTIVE));

        assertThat(relaunch("TO_DELETE"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "ACTION_REFUSED"}""");
    }

    @Test
    void problem_on_vinted_side_is_a_bad_gateway() {
        aValidatedListing();
        deleter.whereSessionIsDisconnected();

        assertThat(relaunch("TO_DELETE"))
                .hasStatus(502)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "SESSION_DISCONNECTED"}""");
    }

    @Test
    void unreadable_wardrobe_is_a_bad_gateway() {
        ListingBackup backup = aValidatedListing();
        deletions.save(ListingDeletion.attempted(backup, NOW.minusSeconds(3605)).confirmed(NOW.minusSeconds(3600)));
        republications.save(ListingRepublication.attempted(backup, NOW.minusSeconds(600)));
        wardrobe.whereTheWardrobeIsUnreadable();

        assertThat(relaunch("REPUBLICATION_TO_CHECK"))
                .hasStatus(502)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "VINTED_PAGE_UNREADABLE"}""");
    }

    @Test
    void unexpected_failure_is_a_server_error() {
        aValidatedListing();
        deleter.whereBrowserFailsUnexpectedly();

        assertThat(relaunch("TO_DELETE"))
                .hasStatus(500)
                .bodyJson().isStrictlyEqualTo("""
                        {"reason": "RELAUNCH_FAILED"}""");
    }

    @Test
    void unknown_stage_is_a_bad_request() {
        assertThat(relaunch("NOWHERE")).hasStatus(400);
    }

    private MockMvcTester.MockMvcRequestBuilder relaunch(String stage) {
        return mvc.post().uri("/api/listings/1/relaunch").contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\": \"" + stage + "\"}");
    }

    private ListingBackup aValidatedListing() {
        listings.saveAll(List.of(Listing.seen(ON_VINTED, SAVED_AT)));
        ListingReading reading = new ListingReading(aContent(1), List.of(aPhoto("front")));
        reading.photos().forEach(photo -> photos.store(reading.content().id(), photo));
        ListingBackup backup = ListingBackup.of(reading, SAVED_AT);
        listingBackups.save(backup);
        validations.save(BackupValidation.of(backup, SAVED_AT));
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
        FakeListingDeleter deleter() {
            return new FakeListingDeleter();
        }

        @Bean
        VintedSession session() {
            return new VintedSession();
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
        RelaunchListing relaunchListing(InMemoryListingRepository listings,
                                        InMemoryListingBackupRepository listingBackups, InMemoryPhotoStorage photos,
                                        InMemoryBackupValidationRepository validations,
                                        InMemoryListingDeletionRepository deletions,
                                        InMemoryListingRepublicationRepository republications,
                                        FakeListingDeleter deleter, FakeWardrobeReader wardrobe,
                                        VintedSession session) {
            InMemoryActionJournal journal = new InMemoryActionJournal();
            AdjustableClock clock = new AdjustableClock(NOW);
            RepublicationDelay delay = new RepublicationDelay(Duration.ofMinutes(5));
            JournalWriter journalWriter = new JournalWriter(journal, clock);
            CadenceCheck cadence = new CadenceCheck(deletions, republications,
                    new WriteCadence(Duration.ofMinutes(2), Duration.ofMinutes(2), 20, 20), clock);
            NewListingSearch newListingSearch = new NewListingSearch(wardrobe, noPause(), listings);
            ShowListingRepublication showRepublication = new ShowListingRepublication(new ShowListingDeletion(listings,
                    listingBackups, photos, validations, deletions, DeletionMode.REAL, delay), republications,
                    listings, RepublicationMode.REAL);
            return new RelaunchListing(
                    new ShowListingDetail(listings, new InMemoryListingStatisticsRepository(), listingBackups,
                            validations, deletions, republications, delay, clock, journal,
                            new InMemoryBackupVerificationRepository(), new InMemoryListingSaleRepository()),
                    deletions, delay,
                    new DeleteListing(session, listings, listingBackups, photos, validations, deletions, deleter,
                            DeletionMode.REAL, delay, cadence, clock, journalWriter),
                    new RecheckListingDeletion(session, listings, listingBackups, photos, validations, deletions,
                            deleter, DeletionMode.REAL, delay, clock, journalWriter),
                    new RepublishListing(session,
                            new RepublicationChecks(listingBackups, photos, validations, deletions, republications,
                                    delay, clock),
                            cadence, republications, new FakeListingPublisher(wardrobe), newListingSearch, listings,
                            RepublicationMode.REAL, clock, showRepublication, journalWriter, (instant, interrupted) -> { }, Duration.ZERO),
                    new VerifyListingRepublication(session, listingBackups, republications, listings,
                            newListingSearch, new FakeListingContentReader(), noBackupPause(), clock,
                            showRepublication, journalWriter));
        }
    }
}
