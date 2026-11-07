package fr.vinted.republisher.adapter.in.web;

import fr.vinted.republisher.application.backup.BackUpWardrobe;
import fr.vinted.republisher.application.backup.ShowBackup;
import fr.vinted.republisher.domain.backup.BackupProblem;
import fr.vinted.republisher.domain.backup.LastBackupProblem;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.WardrobeBackup;
import fr.vinted.republisher.fakes.FakeListingContentReader;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryWardrobeBackupRepository;
import fr.vinted.republisher.application.wardrobe.ScanWardrobe;
import fr.vinted.republisher.application.wardrobe.ShowWardrobe;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.WardrobeScan;
import fr.vinted.republisher.domain.wardrobe.ScanProblem;
import fr.vinted.republisher.domain.wardrobe.LastScanProblem;
import fr.vinted.republisher.fakes.FakeWardrobeReader;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
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

import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.noBackupPause;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.ACTIVE;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.aListing;
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
    private InMemoryListingRepository listings;

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
    private InMemoryListingBackupRepository listingBackups;

    @Test
    void empty_wardrobe_has_no_date_and_no_problem() {
        assertThat(mvc.get().uri("/api/wardrobe"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("{\"scanInProgress\": false, \"backupInProgress\": false, \"listings\": []}");
    }

    @Test
    void shows_the_inventory_and_the_date_of_the_last_scan() {
        listings.saveAll(List.of(Listing.seen(aListing(17, "Pull rayé", "1.5", ACTIVE), SCANNED_AT)));
        scans.save(WardrobeScan.startedAt(SCANNED_AT.minusSeconds(60)).finished(SCANNED_AT));

        assertThat(mvc.get().uri("/api/wardrobe"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"scanInProgress": false, "backupInProgress": false,
                         "lastScanAt": "2026-11-06T10:15:30Z",
                         "listings": [{"vintedId": 17, "title": "Pull rayé",
                                       "price": {"amount": 1.50, "currency": "EUR"},
                                       "url": "https://vinted.test/items/17"}]}""");
    }

    @Test
    void shows_the_problem_of_the_last_scan() {
        lastProblem.report(ScanProblem.VINTED_UNREACHABLE);

        assertThat(mvc.get().uri("/api/wardrobe"))
                .bodyJson().isStrictlyEqualTo(
                        "{\"scanInProgress\": false, \"backupInProgress\": false, \"problem\": \"VINTED_UNREACHABLE\", \"listings\": []}");
    }

    @Test
    void scan_answers_with_the_scan_in_progress_and_runs_it_in_the_background() {
        assertThat(mvc.post().uri("/api/wardrobe/scan"))
                .hasStatus(202)
                .bodyJson().isStrictlyEqualTo("{\"scanInProgress\": true, \"backupInProgress\": false, \"listings\": []}");
        assertThat(reader.reads()).isZero();

        background.runPendingTasks();

        assertThat(reader.reads()).isEqualTo(1);
    }

    @Test
    void ignored_scan_answers_without_starting_anything() {
        session.start(BrowserAction.LOGGING_IN);

        assertThat(mvc.post().uri("/api/wardrobe/scan"))
                .hasStatus(202)
                .bodyJson().isStrictlyEqualTo("{\"scanInProgress\": false, \"backupInProgress\": false, \"listings\": []}");
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
    void shows_the_backup_date_of_each_listing_and_of_the_last_backup() {
        listings.saveAll(List.of(Listing.seen(aListing(17, "Pull rayé", "1.5", ACTIVE), SCANNED_AT),
                Listing.seen(aListing(18, "Jupe", "3", ACTIVE), SCANNED_AT)));
        listingBackups.save(new ListingBackup(aContent(17), SAVED_AT));
        backups.save(WardrobeBackup.startedAt(SAVED_AT).finished(SAVED_AT.plusSeconds(60)));

        assertThat(mvc.get().uri("/api/wardrobe"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {"scanInProgress": false, "backupInProgress": false,
                         "lastBackupAt": "2026-11-07T10:01:00Z",
                         "listings": [{"vintedId": 18, "title": "Jupe",
                                       "price": {"amount": 3.00, "currency": "EUR"},
                                       "url": "https://vinted.test/items/18"},
                                      {"vintedId": 17, "title": "Pull rayé",
                                       "price": {"amount": 1.50, "currency": "EUR"},
                                       "url": "https://vinted.test/items/17",
                                       "savedAt": "2026-11-07T10:00:00Z"}]}""");
    }

    @Test
    void shows_the_problem_of_the_last_backup() {
        lastBackupProblem.report(BackupProblem.SESSION_DISCONNECTED);

        assertThat(mvc.get().uri("/api/wardrobe"))
                .bodyJson().isStrictlyEqualTo("""
                        {"scanInProgress": false, "backupInProgress": false,
                         "backupProblem": "SESSION_DISCONNECTED", "listings": []}""");
    }

    @Test
    void backup_answers_with_the_backup_in_progress_and_runs_it_in_the_background() {
        assertThat(mvc.post().uri("/api/wardrobe/backup"))
                .hasStatus(202)
                .bodyJson().isStrictlyEqualTo("{\"scanInProgress\": false, \"backupInProgress\": true, \"listings\": []}");
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
        ShowWardrobe showWardrobe(VintedSession session, LastScanProblem lastProblem, InMemoryListingRepository listings,
                                  InMemoryWardrobeScanRepository scans) {
            return new ShowWardrobe(session, lastProblem, listings, scans);
        }

        @Bean
        ScanWardrobe scanWardrobe(VintedSession session, LastScanProblem lastProblem, InMemoryWardrobeScanRepository scans,
                                  FakeWardrobeReader reader,
                                  InMemoryListingRepository listings, Background background) {
            return new ScanWardrobe(session, lastProblem, scans, reader, listings, noPause(),
                    Clock.fixed(SCANNED_AT, ZoneOffset.UTC), background);
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
                              InMemoryWardrobeBackupRepository backups, InMemoryListingBackupRepository listingBackups) {
            return new ShowBackup(session, lastBackupProblem, backups, listingBackups);
        }

        @Bean
        BackUpWardrobe backUpWardrobe(VintedSession session, LastBackupProblem lastBackupProblem,
                                      InMemoryWardrobeBackupRepository backups, InMemoryListingRepository listings,
                                      InMemoryListingBackupRepository listingBackups, Background background) {
            return new BackUpWardrobe(session, lastBackupProblem, backups, new FakeListingContentReader(), listings,
                    listingBackups, noBackupPause(), Clock.fixed(SAVED_AT, ZoneOffset.UTC), background);
        }
    }
}
