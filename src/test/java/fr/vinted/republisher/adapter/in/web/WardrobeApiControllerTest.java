package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.application.backup.BackUpWardrobe;
import fr.vinted.republisher.application.backup.ShowBackup;
import fr.vinted.republisher.application.journal.JournalWriter;
import fr.vinted.republisher.application.sale.NoticeSales;
import fr.vinted.republisher.domain.backup.BackupProblem;
import fr.vinted.republisher.domain.backup.LastBackupProblem;
import fr.vinted.republisher.domain.backup.WardrobeBackup;
import fr.vinted.republisher.fakes.FakeListingContentReader;
import fr.vinted.republisher.fakes.InMemoryActionJournal;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
import fr.vinted.republisher.fakes.InMemoryWardrobeBackupRepository;
import fr.vinted.republisher.application.wardrobe.RefreshStatistics;
import fr.vinted.republisher.application.wardrobe.ScanWardrobe;
import fr.vinted.republisher.application.wardrobe.ShowStatisticsRefresh;
import fr.vinted.republisher.application.wardrobe.ShowWardrobe;
import fr.vinted.republisher.domain.wardrobe.LastStatisticsRefreshProblem;
import fr.vinted.republisher.domain.wardrobe.ListingStatistics;
import fr.vinted.republisher.domain.wardrobe.StatisticsRefreshProblem;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.WardrobeScan;
import fr.vinted.republisher.domain.wardrobe.ScanProblem;
import fr.vinted.republisher.domain.wardrobe.LastScanProblem;
import fr.vinted.republisher.fakes.FakeWardrobeReader;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryListingSaleRepository;
import fr.vinted.republisher.fakes.InMemoryListingStatisticsRepository;
import fr.vinted.republisher.fakes.InMemoryWardrobeScanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static fr.vinted.republisher.domain.backup.BackupFixtures.noBackupPause;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.noPause;
import static org.assertj.core.api.Assertions.assertThat;

/** AT-04-13: the API answers as described by the OpenAPI contract. */
// Each test starts from an empty inventory and no browser action.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebMvcTest(WardrobeApiController.class)
class WardrobeApiControllerTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-11-06T10:15:30Z");
    private static final Instant SAVED_AT = Instant.parse("2026-11-07T10:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private VintedSession session;

    @Autowired
    private LastScanProblem lastProblem;

    @Autowired
    private InMemoryWardrobeScanRepository scans;

    @Autowired
    private FakeWardrobeReader reader;

    @Autowired
    private Background background;

    @Autowired
    private LastBackupProblem lastBackupProblem;

    @Autowired
    private InMemoryWardrobeBackupRepository backups;

    @Autowired
    private LastStatisticsRefreshProblem lastStatisticsRefreshProblem;

    @Test
    void empty_wardrobe_has_no_date_and_no_problem() {
        assertThat(mvc.get().uri("/api/wardrobe"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("{\"scanInProgress\": false, \"backupInProgress\": false, \"statisticsRefreshInProgress\": false}");
    }

    @Test
    void shows_the_date_of_the_last_scan() {
        scans.save(WardrobeScan.startedAt(SCANNED_AT.minusSeconds(60)).finished(SCANNED_AT));

        assertThat(mvc.get().uri("/api/wardrobe"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"scanInProgress": false, "backupInProgress": false, "statisticsRefreshInProgress": false,
                         "lastScanAt": "2026-11-06T10:15:30Z"}""");
    }

    @Test
    void shows_the_date_of_the_last_backup() {
        backups.save(WardrobeBackup.startedAt(SAVED_AT).finished(SAVED_AT.plusSeconds(60)));

        assertThat(mvc.get().uri("/api/wardrobe"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"scanInProgress": false, "backupInProgress": false, "statisticsRefreshInProgress": false,
                         "lastBackupAt": "2026-11-07T10:01:00Z"}""");
    }

    @Test
    void shows_the_problem_of_the_last_scan() {
        lastProblem.report(ScanProblem.VINTED_UNREACHABLE);

        assertThat(mvc.get().uri("/api/wardrobe"))
                .bodyJson().isStrictlyEqualTo(
                        "{\"scanInProgress\": false, \"backupInProgress\": false, \"statisticsRefreshInProgress\": false, \"problem\": \"VINTED_UNREACHABLE\"}");
    }

    @Test
    void scan_answers_with_the_scan_in_progress_and_runs_it_in_the_background() {
        assertThat(mvc.post().uri("/api/wardrobe/scan"))
                .hasStatus(202)
                .bodyJson().isStrictlyEqualTo("{\"scanInProgress\": true, \"backupInProgress\": false, \"statisticsRefreshInProgress\": false}");
        assertThat(reader.reads()).isZero();

        background.runPendingTasks();

        assertThat(reader.reads()).isEqualTo(1);
    }

    @Test
    void ignored_scan_answers_without_starting_anything() {
        session.start(BrowserAction.LOGGING_IN);

        assertThat(mvc.post().uri("/api/wardrobe/scan"))
                .hasStatus(202)
                .bodyJson().isStrictlyEqualTo("{\"scanInProgress\": false, \"backupInProgress\": false, \"statisticsRefreshInProgress\": false}");
        assertThat(background.pendingTasks).isEmpty();
    }

    @Test
    void scan_requested_by_another_site_is_refused() {
        // Étant donné un autre site ouvert dans mon navigateur
        // Quand il demande un scan à l'application
        // Alors la demande est refusée
        // Et aucun scan n'est lancé
        assertThat(mvc.post().uri("/api/wardrobe/scan").header("Origin", "https://example.test"))
                .hasStatus(403);
        assertThat(background.pendingTasks).isEmpty();
    }

    @Test
    void same_address_on_another_port_is_another_site() {
        assertThat(mvc.post().uri("/api/wardrobe/scan").header("Origin", "http://localhost:3000"))
                .hasStatus(403);
    }

    @Test
    void sandboxed_page_without_an_origin_of_its_own_is_refused() {
        assertThat(mvc.post().uri("/api/wardrobe/scan").header("Origin", "null"))
                .hasStatus(403);
    }

    @Test
    void scan_requested_by_the_page_of_the_application_is_accepted() {
        assertThat(mvc.post().uri("/api/wardrobe/scan").header("Origin", "http://localhost"))
                .hasStatus(202);
    }

    @Test
    void shows_the_problem_of_the_last_backup() {
        lastBackupProblem.report(BackupProblem.SESSION_DISCONNECTED);

        assertThat(mvc.get().uri("/api/wardrobe"))
                .bodyJson().isStrictlyEqualTo("""
                        {"scanInProgress": false, "backupInProgress": false, "statisticsRefreshInProgress": false,
                         "backupProblem": "SESSION_DISCONNECTED"}""");
    }

    @Test
    void backup_answers_with_the_backup_in_progress_and_runs_it_in_the_background() {
        assertThat(mvc.post().uri("/api/wardrobe/backup"))
                .hasStatus(202)
                .bodyJson().isStrictlyEqualTo("{\"scanInProgress\": false, \"backupInProgress\": true, \"statisticsRefreshInProgress\": false}");
        assertThat(background.pendingTasks).hasSize(1);

        background.runPendingTasks();

        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void backup_from_another_origin_is_forbidden() {
        // Étant donné un autre site ouvert dans mon navigateur
        // Quand il envoie POST /api/wardrobe/backup
        // Alors la réponse est 403 et aucune sauvegarde n'est lancée
        assertThat(mvc.post().uri("/api/wardrobe/backup").header("Origin", "https://example.test"))
                .hasStatus(403);
        assertThat(background.pendingTasks).isEmpty();
    }

    @Test
    void reading_is_open_to_any_origin_as_it_changes_nothing() {
        assertThat(mvc.get().uri("/api/wardrobe").header("Origin", "https://example.test"))
                .hasStatusOk();
    }

    @Test
    void statistics_refresh_is_accepted_and_shows_the_overview() {
        assertThat(mvc.post().uri("/api/wardrobe/statistics-refresh"))
                .hasStatus(202)
                .bodyJson().isStrictlyEqualTo("""
                        {"scanInProgress": false, "backupInProgress": false, "statisticsRefreshInProgress": true}""");
        reader.holding(anActiveListing(1)).showing(1, new ListingStatistics(4, 1));

        background.runPendingTasks();

        assertThat(mvc.get().uri("/api/wardrobe"))
                .bodyJson().isStrictlyEqualTo("""
                        {"scanInProgress": false, "backupInProgress": false, "statisticsRefreshInProgress": false,
                         "lastStatisticsReadAt": "2026-11-06T10:15:30Z"}""");
    }

    @Test
    void shows_the_problem_of_the_last_statistics_refresh() {
        lastStatisticsRefreshProblem.report(StatisticsRefreshProblem.NO_STATISTICS_SHOWN);

        assertThat(mvc.get().uri("/api/wardrobe"))
                .bodyJson().isStrictlyEqualTo("""
                        {"scanInProgress": false, "backupInProgress": false, "statisticsRefreshInProgress": false,
                         "statisticsRefreshProblem": "NO_STATISTICS_SHOWN"}""");
    }

    @Test
    void statistics_refresh_from_another_origin_is_forbidden() {
        assertThat(mvc.post().uri("/api/wardrobe/statistics-refresh").header("Origin", "https://example.test"))
                .hasStatus(403);
        assertThat(background.pendingTasks).isEmpty();
    }

    /** Holds the scan until the test runs it, as a background thread would later. */
    static final class Background implements Executor {

        private final List<Runnable> pendingTasks = new ArrayList<>();

        @Override
        public void execute(Runnable task) {
            pendingTasks.add(task);
        }

        void runPendingTasks() {
            pendingTasks.forEach(Runnable::run);
            pendingTasks.clear();
        }
    }

    @TestConfiguration
    static class WardrobeUseCases {

        @Bean
        VintedSession session() {
            return new VintedSession();
        }

        @Bean
        LastScanProblem lastProblem() {
            return new LastScanProblem();
        }

        @Bean
        InMemoryListingRepository listings() {
            return new InMemoryListingRepository();
        }

        @Bean
        InMemoryListingStatisticsRepository listingStatistics() {
            return new InMemoryListingStatisticsRepository();
        }

        @Bean
        InMemoryWardrobeScanRepository scans() {
            return new InMemoryWardrobeScanRepository();
        }

        @Bean
        FakeWardrobeReader reader() {
            return new FakeWardrobeReader();
        }

        @Bean
        Background background() {
            return new Background();
        }

        @Bean
        ShowWardrobe showWardrobe(VintedSession session, LastScanProblem lastProblem, InMemoryWardrobeScanRepository scans) {
            return new ShowWardrobe(session, lastProblem, scans);
        }

        @Bean
        ScanWardrobe scanWardrobe(VintedSession session, LastScanProblem lastProblem, InMemoryWardrobeScanRepository scans,
                                  FakeWardrobeReader reader,
                                  InMemoryListingRepository listings, InMemoryListingStatisticsRepository statistics,
                                  Background background) {
            return new ScanWardrobe(session, lastProblem, scans, reader, listings, statistics,
                    new NoticeSales(new InMemoryListingRepository(), new InMemoryListingSaleRepository(), Clock.systemUTC()), noPause(),
                    Clock.fixed(SCANNED_AT, ZoneOffset.UTC), background,
                    new JournalWriter(new InMemoryActionJournal(), Clock.systemUTC()));
        }

        @Bean
        LastStatisticsRefreshProblem lastStatisticsRefreshProblem() {
            return new LastStatisticsRefreshProblem();
        }

        @Bean
        ShowStatisticsRefresh showStatisticsRefresh(VintedSession session, LastStatisticsRefreshProblem lastProblem,
                                                    InMemoryListingStatisticsRepository statistics) {
            return new ShowStatisticsRefresh(session, lastProblem, statistics);
        }

        @Bean
        RefreshStatistics refreshStatistics(VintedSession session, LastStatisticsRefreshProblem lastProblem,
                                            FakeWardrobeReader reader, InMemoryListingStatisticsRepository statistics,
                                            Background background) {
            return new RefreshStatistics(session, lastProblem, reader, statistics,
                    new NoticeSales(new InMemoryListingRepository(), new InMemoryListingSaleRepository(), Clock.systemUTC()), noPause(),
                    Clock.fixed(SCANNED_AT, ZoneOffset.UTC), background,
                    new JournalWriter(new InMemoryActionJournal(), Clock.systemUTC()));
        }

        @Bean
        LastBackupProblem lastBackupProblem() {
            return new LastBackupProblem();
        }

        @Bean
        InMemoryWardrobeBackupRepository backups() {
            return new InMemoryWardrobeBackupRepository();
        }

        @Bean
        InMemoryListingBackupRepository listingBackups() {
            return new InMemoryListingBackupRepository();
        }

        @Bean
        ShowBackup showBackup(VintedSession session, LastBackupProblem lastBackupProblem,
                              InMemoryWardrobeBackupRepository backups) {
            return new ShowBackup(session, lastBackupProblem, backups);
        }

        @Bean
        BackUpWardrobe backUpWardrobe(VintedSession session, LastBackupProblem lastBackupProblem,
                                      InMemoryWardrobeBackupRepository backups, InMemoryListingRepository listings,
                                      InMemoryListingBackupRepository listingBackups, Background background) {
            return new BackUpWardrobe(session, lastBackupProblem, backups, new FakeListingContentReader(), listings,
                    listingBackups, new InMemoryPhotoStorage(), noBackupPause(), Clock.fixed(SAVED_AT, ZoneOffset.UTC),
                    background, new JournalWriter(new InMemoryActionJournal(), Clock.systemUTC()));
        }
    }
}
