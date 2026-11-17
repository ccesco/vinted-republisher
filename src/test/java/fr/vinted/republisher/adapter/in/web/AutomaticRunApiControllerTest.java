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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static fr.vinted.republisher.domain.backup.BackupFixtures.noBackupPause;
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
                        {"status": "IDLE", "relaunchCount": 0, "skippedListingIds": []}""");
    }

    @Test
    void launch_is_accepted_and_shows_the_run_started() {
        assertThat(mvc.post().uri("/api/automatic-run"))
                .hasStatus(202)
                .bodyJson().isStrictlyEqualTo("""
                        {"status": "RUNNING", "startedAt": "2026-11-17T10:00:00Z", "relaunchCount": 0,
                         "skippedListingIds": []}""");
        assertThat(launched).hasSize(1);
    }

    @Test
    void a_waiting_run_shows_its_date_and_listing() {
        AutomaticRun waiting = AutomaticRun.startedAt(NOW).relaunching(new VintedListingId(7)).relaunched()
                .skipping(new VintedListingId(3)).waitingUntil(NOW.plusSeconds(300));

        assertThat(AutomaticRunApiController.toDto(waiting)).satisfies(dto -> {
            assertThat(dto.getStatus().name()).isEqualTo("WAITING");
            assertThat(dto.getWaitingUntil()).hasToString("2026-11-17T10:05Z");
            assertThat(dto.getCurrentListingId()).isEqualTo(7L);
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
            assertThat(dto.getCurrentListingId()).isNull();
        });
    }

    @TestConfiguration
    static class Fakes {

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
                    new WriteCadence(Duration.ofMinutes(2), 20, 20), clock);
            NewListingSearch search = new NewListingSearch(wardrobe, noPause(), listings);
            ShowListingRepublication show = new ShowListingRepublication(new ShowListingDeletion(listings, backups,
                    photos, validations, deletions, DeletionMode.REAL, delay), republications, listings,
                    RepublicationMode.REAL);
            RelaunchListing relaunch = new RelaunchListing(new ShowListingDetail(listings, statistics, backups,
                    validations, deletions, republications, delay, clock, journal), deletions, delay,
                    new DeleteListing(session, listings, backups, photos, validations, deletions, deleter,
                            DeletionMode.REAL, delay, cadence, clock, journalWriter),
                    new RecheckListingDeletion(session, listings, backups, photos, validations, deletions, deleter,
                            DeletionMode.REAL, delay, clock, journalWriter),
                    new RepublishListing(session, new RepublicationChecks(backups, photos, validations, deletions,
                            republications, delay, clock), cadence, republications,
                            new FakeListingPublisher(wardrobe), search, listings, RepublicationMode.REAL, clock, show,
                            journalWriter),
                    new VerifyListingRepublication(session, backups, republications, listings, search,
                            new FakeListingContentReader(), noBackupPause(), clock, show));
            return new LaunchAutomaticRun(new ShowListingInventory(listings, statistics, backups, validations,
                    deletions, republications, delay, clock), relaunch, until -> {
            }, clock, launched::add);
        }
    }
}
