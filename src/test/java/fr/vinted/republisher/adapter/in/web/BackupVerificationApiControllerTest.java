package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.application.backup.VerifyAndValidateBackups;
import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.fakes.FakeListingContentReader;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryBackupValidationRepository;
import fr.vinted.republisher.fakes.InMemoryBackupVerificationRepository;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingDeletionRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepublicationRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import fr.vinted.republisher.fakes.InMemoryListingTagRepository;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static fr.vinted.republisher.domain.backup.BackupFixtures.noBackupPause;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;

/** AT-40-10 and AT-40-13: the API answers as described by the OpenAPI contract. */
// Each test starts with no verification and no browser action.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(BackupVerificationApiController.class)
class BackupVerificationApiControllerTest {

    private static final Instant SAVED_AT = Instant.parse("2026-11-10T10:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private VintedSession session;

    @Autowired
    private AtomicBoolean automaticRunInProgress;

    @Autowired
    private WardrobeApiControllerTest.Background background;

    @Autowired
    private InMemoryListingRepository listings;

    @Autowired
    private InMemoryListingBackupRepository listingBackups;

    @Autowired
    private InMemoryPhotoStorage photos;

    @Autowired
    private FakeListingContentReader reader;

    @Test
    void no_verification_since_the_start_is_idle() {
        assertThat(mvc.get().uri("/api/backup-verifications/current"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"status": "IDLE", "verifiedCount": 0, "totalCount": 0, "validatedCount": 0,
                         "failedCount": 0}""");
    }

    @Test
    void launch_answers_with_the_verification_in_progress_and_runs_it_in_the_background() {
        aKeptBackupOf(101);
        aKeptBackupOf(102);
        reader.showing(aContent(101), aContent(102, "Pull rayé 102", "Porté une fois"));

        assertThat(mvc.post().uri("/api/backup-verifications"))
                .hasStatus(202)
                .bodyJson().isStrictlyEqualTo("""
                        {"status": "RUNNING", "verifiedCount": 0, "totalCount": 2, "validatedCount": 0,
                         "failedCount": 0}""");
        assertThat(reader.opened()).isEmpty();

        background.runPendingTasks();

        assertThat(mvc.get().uri("/api/backup-verifications/current"))
                .bodyJson().isStrictlyEqualTo("""
                        {"status": "FINISHED", "verifiedCount": 2, "totalCount": 2, "validatedCount": 1,
                         "failedCount": 1}""");
    }

    @Test
    void shows_why_the_verification_stopped() {
        aKeptBackupOf(101);
        reader.whereFormRedirectsToLogin(101);
        mvc.post().uri("/api/backup-verifications").exchange();

        background.runPendingTasks();

        assertThat(mvc.get().uri("/api/backup-verifications/current"))
                .bodyJson().isStrictlyEqualTo("""
                        {"status": "STOPPED", "verifiedCount": 0, "totalCount": 1, "validatedCount": 0,
                         "failedCount": 0, "stopReason": "SESSION_DISCONNECTED"}""");
    }

    @Test
    void refused_while_another_action_runs() {
        session.start(BrowserAction.BACKING_UP);

        assertThat(mvc.post().uri("/api/backup-verifications"))
                .hasStatus(409)
                .bodyJson().isStrictlyEqualTo("{\"reason\": \"BROWSER_BUSY\"}");
        session.finish();
        automaticRunInProgress.set(true);
        assertThat(mvc.post().uri("/api/backup-verifications")).hasStatus(409);
        assertThat(mvc.get().uri("/api/backup-verifications/current"))
                .bodyJson().extractingPath("$.status").isEqualTo("IDLE");
    }

    @Test
    void refused_while_a_verification_runs() {
        aKeptBackupOf(101);
        reader.showing(aContent(101));
        mvc.post().uri("/api/backup-verifications").exchange();

        assertThat(mvc.post().uri("/api/backup-verifications")).hasStatus(409);
    }

    @Test
    void request_from_another_site_is_refused() {
        assertThat(mvc.post().uri("/api/backup-verifications").header("Origin", "https://example.test"))
                .hasStatus(403);
        assertThat(session.overview().currentAction()).isEmpty();
        assertThat(reader.tabs()).isZero();
    }

    private void aKeptBackupOf(long id) {
        listings.saveAll(List.of(Listing.seen(anActiveListing(id), SAVED_AT)));
        ListingReading reading = new ListingReading(aContent(id), List.of(aPhoto("of " + id)));
        reading.photos().forEach(photo -> photos.store(reading.id(), photo));
        listingBackups.save(ListingBackup.of(reading, SAVED_AT));
    }

    @TestConfiguration
    static class VerificationUseCases {

        @Bean
        VintedSession session() {
            return new VintedSession();
        }

        @Bean
        AtomicBoolean automaticRunInProgress() {
            return new AtomicBoolean();
        }

        @Bean
        WardrobeApiControllerTest.Background background() {
            return new WardrobeApiControllerTest.Background();
        }

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
        FakeListingContentReader reader() {
            return new FakeListingContentReader();
        }

        @Bean
        VerifyAndValidateBackups verifyAndValidateBackups(VintedSession session, AtomicBoolean automaticRunInProgress,
                                                          InMemoryListingRepository listings,
                                                          InMemoryListingBackupRepository listingBackups,
                                                          InMemoryPhotoStorage photos, FakeListingContentReader reader,
                                                          WardrobeApiControllerTest.Background background) {
            Clock clock = Clock.fixed(SAVED_AT.plusSeconds(3600), ZoneOffset.UTC);
            InMemoryBackupValidationRepository validations = new InMemoryBackupValidationRepository();
            InMemoryBackupVerificationRepository verifications = new InMemoryBackupVerificationRepository(validations);
            ShowListingInventory inventory = new ShowListingInventory(listings,
                    new InMemoryListingStatisticsRepository(), listingBackups, validations,
                    new InMemoryListingDeletionRepository(), new InMemoryListingRepublicationRepository(),
                    new RepublicationDelay(Duration.ofMinutes(5)), clock, new InMemoryListingTagRepository(),
                    verifications);
            return new VerifyAndValidateBackups(session, automaticRunInProgress::get, inventory, reader,
                    listingBackups, photos, validations, verifications, noBackupPause(), clock, background,
                    new JournalWriter(new InMemoryActionJournal(), clock));
        }
    }
}
