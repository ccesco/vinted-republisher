package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.application.cadence.CadenceCheck;
import fr.vinted.republisher.application.deletion.DeleteListing;
import fr.vinted.republisher.application.deletion.RecheckListingDeletion;
import fr.vinted.republisher.application.deletion.ShowListingDeletion;
import fr.vinted.republisher.application.inventory.ShowListingDetail;
import fr.vinted.republisher.application.inventory.ShowListingInventory;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.application.relaunch.RelaunchListing;
import fr.vinted.republisher.application.republication.NewListingSearch;
import fr.vinted.republisher.application.republication.RepublicationChecks;
import fr.vinted.republisher.application.republication.RepublishListing;
import fr.vinted.republisher.application.republication.ShowListingRepublication;
import fr.vinted.republisher.application.republication.VerifyListingRepublication;
import fr.vinted.republisher.application.run.LaunchAutomaticRun;
import fr.vinted.republisher.application.run.ShowRunQueue;
import fr.vinted.republisher.domain.backup.BackupValidation;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.run.WriteModes;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.cadence.WriteCadence;
import fr.vinted.republisher.domain.deletion.DeletionMode;
import fr.vinted.republisher.domain.republication.RepublicationDelay;
import fr.vinted.republisher.domain.republication.RepublicationMode;
import fr.vinted.republisher.domain.run.AutomaticRun;
import fr.vinted.republisher.domain.run.RunStopReason;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.fakes.AdjustableClock;
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
import fr.vinted.republisher.fakes.InMemoryListingTagRepository;
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.backup.BackupFixtures.noBackupPause;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.noPause;
import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(AutomaticRunApiController.class)
class AutomaticRunApiControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private List<Runnable> launched;

    @Test
    void no_run_since_startup_is_idle() {
        assertThat(mvc.get().uri("/api/automatic-run"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"status": "IDLE", "currentListingIds": [], "relaunchCount": 0, "skippedListingIds": [],
                         "pauseRequested": false, "stopRequested": false}""");
    }

    @Test
    void launch_is_accepted_and_shows_the_run_started() {
        assertThat(mvc.post().uri("/api/automatic-run"))
                .hasStatus(202)
                .bodyJson().isStrictlyEqualTo("""
                        {"status": "RUNNING", "startedAt": "2026-11-17T10:00:00Z", "currentListingIds": [],
                         "relaunchCount": 0, "skippedListingIds": [], "pauseRequested": false,
                         "stopRequested": false}""");
        assertThat(launched).hasSize(1);
    }

    @Test
    void pause_resume_and_stop_are_accepted_and_show_the_requests() {
        mvc.post().uri("/api/automatic-run").exchange();

        assertThat(mvc.post().uri("/api/automatic-run/pause"))
                .hasStatus(202)
                .bodyJson().extractingPath("$.pauseRequested").isEqualTo(true);
        assertThat(mvc.post().uri("/api/automatic-run/resume"))
                .hasStatus(202)
                .bodyJson().extractingPath("$.pauseRequested").isEqualTo(false);
        assertThat(mvc.post().uri("/api/automatic-run/stop"))
                .hasStatus(202)
                .bodyJson().extractingPath("$.stopRequested").isEqualTo(true);
    }

    @Test
    void requests_without_a_run_are_ignored() {
        assertThat(mvc.post().uri("/api/automatic-run/stop"))
                .hasStatus(202)
                .bodyJson().satisfies(body -> {
                    assertThat(body).extractingPath("$.status").isEqualTo("IDLE");
                    assertThat(body).extractingPath("$.stopRequested").isEqualTo(false);
                });
    }

    @Test
    void a_run_stopped_by_the_member_and_a_paused_run() {
        AutomaticRun started = AutomaticRun.startedAt(NOW);

        assertThat(AutomaticRunApiController.toDto(started.withPauseRequested().pausedIfRequested()).getStatus().name()).isEqualTo("PAUSED");
        assertThat(AutomaticRunApiController.toDto(started.stoppedAt(NOW, RunStopReason.STOPPED_BY_MEMBER))
                .getStopReason().name()).isEqualTo("STOPPED_BY_MEMBER");
    }

    @Test
    void a_waiting_run_shows_its_date_and_the_listings_of_its_round() {
        AutomaticRun waiting = AutomaticRun.startedAt(NOW)
                .relaunching(List.of(new VintedListingId(7), new VintedListingId(8))).relaunched()
                .skipping(new VintedListingId(3)).waitingUntil(NOW.plusSeconds(300));

        assertThat(AutomaticRunApiController.toDto(waiting)).satisfies(dto -> {
            assertThat(dto.getStatus().name()).isEqualTo("WAITING");
            assertThat(dto.getWaitingUntil()).hasToString("2026-11-17T10:05Z");
            assertThat(dto.getCurrentListingIds()).containsExactly(7L, 8L);
            assertThat(dto.getRelaunchCount()).isEqualTo(1);
            assertThat(dto.getSkippedListingIds()).containsExactly(3L);
        });
    }

    @Test
    void a_stopped_run_shows_its_reason_and_end() {
        AutomaticRun stopped = AutomaticRun.startedAt(NOW).stoppedAt(NOW.plusSeconds(60),
                RunStopReason.SESSION_DISCONNECTED);

        assertThat(AutomaticRunApiController.toDto(stopped)).satisfies(dto -> {
            assertThat(dto.getStatus().name()).isEqualTo("STOPPED");
            assertThat(dto.getStopReason().name()).isEqualTo("SESSION_DISCONNECTED");
            assertThat(dto.getEndedAt()).hasToString("2026-11-17T10:01Z");
            assertThat(dto.getCurrentListingIds()).isEmpty();
        });
    }

    @Test
    void shows_the_queue() {
        // AT-41-06: listing 1 ready to delete, real writes: its deletion now, then its republication after the delay
        assertThat(mvc.get().uri("/api/automatic-run/queue"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"computedAt": "2026-11-17T10:00:00Z", "actions": [
                          {"listingId": 1, "listingTitle": "Robe bleue", "action": "DELETION",
                           "earliestAt": "2026-11-17T10:00:00Z", "dryRun": false},
                          {"listingId": 1, "listingTitle": "Robe bleue", "action": "REPUBLICATION",
                           "earliestAt": "2026-11-17T10:05:00Z", "dryRun": false}]}""");
    }

    @TestConfiguration
    static class Fakes {

        /** Listing 1 backed up and validated, real writes, no write yet. */
        @Bean
        ShowRunQueue showRunQueue() {
            InMemoryListingRepository listings = new InMemoryListingRepository();
            InMemoryListingBackupRepository backups = new InMemoryListingBackupRepository();
            InMemoryBackupValidationRepository validations = new InMemoryBackupValidationRepository();
            InMemoryListingDeletionRepository deletions = new InMemoryListingDeletionRepository();
            InMemoryListingRepublicationRepository republications = new InMemoryListingRepublicationRepository();
            AdjustableClock clock = new AdjustableClock(NOW);
            RepublicationDelay delay = new RepublicationDelay(Duration.ofMinutes(5));
            listings.saveAll(List.of(Listing.seen(aListing(1, "Robe bleue", "12.00", ACTIVE), NOW)));
            ListingBackup backup = aBackup(1, NOW);
            backups.save(backup);
            validations.save(BackupValidation.of(backup, NOW));
            return new ShowRunQueue(new ShowListingInventory(listings, new InMemoryListingStatisticsRepository(),
                    backups, validations, deletions, republications, delay, clock, new InMemoryListingTagRepository(),
                    new InMemoryBackupVerificationRepository()),
                    deletions, delay, new CadenceCheck(deletions, republications,
                    new WriteCadence(Duration.ofSeconds(30), Duration.ofMinutes(2), 20, 20), clock),
                    new WriteModes(DeletionMode.REAL, RepublicationMode.REAL), AutomaticRun::idle, clock);
        }

        @Bean
        List<Runnable> launched() {
            return new ArrayList<>();
        }

        /** The run is kept, not run: the page only needs its overview. */
        @Bean
        LaunchAutomaticRun launchAutomaticRun(List<Runnable> launched) {
            InMemoryListingRepository listings = new InMemoryListingRepository();
            InMemoryListingBackupRepository backups = new InMemoryListingBackupRepository();
            InMemoryPhotoStorage photos = new InMemoryPhotoStorage();
            InMemoryBackupValidationRepository validations = new InMemoryBackupValidationRepository();
            InMemoryListingDeletionRepository deletions = new InMemoryListingDeletionRepository();
            InMemoryListingRepublicationRepository republications = new InMemoryListingRepublicationRepository();
            InMemoryListingStatisticsRepository statistics = new InMemoryListingStatisticsRepository();
            InMemoryActionJournal journal = new InMemoryActionJournal();
            AdjustableClock clock = new AdjustableClock(NOW);
            RepublicationDelay delay = new RepublicationDelay(Duration.ofMinutes(5));
            VintedSession session = new VintedSession();
            FakeListingDeleter deleter = new FakeListingDeleter();
            FakeWardrobeReader wardrobe = new FakeWardrobeReader();
            JournalWriter journalWriter = new JournalWriter(journal, clock);
            CadenceCheck cadence = new CadenceCheck(deletions, republications,
                    new WriteCadence(Duration.ofMinutes(2), Duration.ofMinutes(2), 20, 20), clock);
            NewListingSearch search = new NewListingSearch(wardrobe, noPause(), listings);
            ShowListingRepublication show = new ShowListingRepublication(new ShowListingDeletion(listings, backups,
                    photos, validations, deletions, DeletionMode.REAL, delay), republications, listings,
                    RepublicationMode.REAL);
            RelaunchListing relaunch = new RelaunchListing(new ShowListingDetail(listings, statistics, backups,
                    validations, deletions, republications, delay, clock, journal,
                    new InMemoryBackupVerificationRepository()), deletions, delay,
                    new DeleteListing(session, listings, backups, photos, validations, deletions, deleter,
                            DeletionMode.REAL, delay, cadence, clock, journalWriter),
                    new RecheckListingDeletion(session, listings, backups, photos, validations, deletions, deleter,
                            DeletionMode.REAL, delay, clock, journalWriter),
                    new RepublishListing(session, new RepublicationChecks(backups, photos, validations, deletions,
                            republications, delay, clock), cadence, republications,
                            new FakeListingPublisher(wardrobe), search, listings, RepublicationMode.REAL, clock, show,
                            journalWriter, (instant, interrupted) -> { }, Duration.ZERO),
                    new VerifyListingRepublication(session, backups, republications, listings, search,
                            new FakeListingContentReader(), noBackupPause(), clock, show,
                            new JournalWriter(new InMemoryActionJournal(), clock)));
            return new LaunchAutomaticRun(new ShowListingInventory(listings, statistics, backups, validations,
                    deletions, republications, delay, clock, new InMemoryListingTagRepository(),
                    new InMemoryBackupVerificationRepository()), relaunch,
                    (until, interrupted) -> {
                    }, clock, launched::add, 1, Runnable::run);
        }
    }
}
