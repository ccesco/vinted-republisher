package fr.vinted.republisher.application.backup;

import fr.vinted.republisher.domain.backup.BackupPace;
import fr.vinted.republisher.domain.backup.BackupProblem;
import fr.vinted.republisher.domain.backup.LastBackupProblem;
import fr.vinted.republisher.domain.backup.ListingBackup;
import fr.vinted.republisher.domain.backup.ListingContent;
import fr.vinted.republisher.domain.backup.ListingPhoto;
import fr.vinted.republisher.domain.backup.ListingReading;
import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.SessionOverview;
import fr.vinted.republisher.domain.session.SessionStatus;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.PauseRange;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.FakeListingContentReader;
import fr.vinted.republisher.fakes.InMemoryListingBackupRepository;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryPhotoStorage;
import fr.vinted.republisher.fakes.InMemoryWardrobeBackupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.LongStream;

import static fr.vinted.republisher.domain.backup.BackupFixtures.aBackup;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aContent;
import static fr.vinted.republisher.domain.backup.BackupFixtures.aPhoto;
import static fr.vinted.republisher.domain.backup.BackupFixtures.noBackupPause;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@ExtendWith(OutputCaptureExtension.class)
class BackUpWardrobeTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(3600);
    private static final Duration MINIMUM_PAUSE = Duration.ofSeconds(10);
    private static final Duration MAXIMUM_PAUSE = Duration.ofSeconds(15);

    private final VintedSession session = new VintedSession();
    private final LastBackupProblem lastProblem = new LastBackupProblem();
    private final InMemoryWardrobeBackupRepository backups = new InMemoryWardrobeBackupRepository();
    private final InMemoryListingBackupRepository listingBackups = new InMemoryListingBackupRepository();
    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final FakeListingContentReader reader = new FakeListingContentReader();
    private final InMemoryPhotoStorage photos = new InMemoryPhotoStorage();
    private final AdjustableClock clock = new AdjustableClock(NOW);
    private final List<Runnable> background = new ArrayList<>();

    @Test
    void shows_the_backup_in_progress_then_runs_it_in_the_background() {
        anInventoryOf(1);
        reader.showing(aContent(1));

        backUpWardrobe(background::add, noBackupPause()).backUp();

        assertThat(session.overview().currentAction()).contains(BrowserAction.BACKING_UP);
        assertThat(reader.tabs()).isZero();

        background.forEach(Runnable::run);

        assertThat(session.overview().currentAction()).isEmpty();
        assertThat(listingBackups.findAll()).hasSize(1);
    }

    @Test
    void each_listing_is_dated_with_the_moment_it_was_read() {
        clock.advancingAtEachReadingBy(Duration.ofSeconds(1));
        anInventoryOf(1, 2);
        reader.showing(aContent(1), aContent(2));

        backUp();

        assertThat(listingBackups.findAll()).extracting(ListingBackup::savedAt)
                .containsExactly(NOW.plusSeconds(1), NOW.plusSeconds(2));
    }

    @Test
    void successful_backup_does_not_change_what_is_known_of_the_session() {
        session.start(BrowserAction.CHECKING);
        session.complete(SessionStatus.CONNECTED);
        anInventoryOf(1);
        reader.showing(aContent(1));

        backUp();

        assertThat(session.overview()).isEqualTo(SessionOverview.idle(SessionStatus.CONNECTED));
    }

    @Test
    void failed_backup_stays_unfinished_and_resumes_with_its_start() {
        anInventoryOf(1);
        reader.whereVintedRefuses(1);
        backUp();
        clock.advance(Duration.ofHours(1));
        reader.afresh().showing(aContent(1));

        backUp();

        assertThat(backups.findLastFinished()).hasValueSatisfying(backup -> {
            assertThat(backup.startedAt()).isEqualTo(NOW);
            assertThat(backup.finishedAt()).contains(NOW.plus(Duration.ofHours(1)));
        });
    }

    @Test
    void backup_after_a_finished_one_reads_every_listing_again() {
        anInventoryOf(1);
        reader.showing(aContent(1));
        backUp();
        clock.advance(Duration.ofHours(1));
        reader.afresh();

        backUp();

        assertThat(reader.opened()).hasSize(1);
        assertThat(listingBackups.findAll()).containsExactly(aBackup(1, NOW.plus(Duration.ofHours(1))));
    }

    @Test
    void empty_inventory_finishes_at_once() {
        backUp();

        assertThat(reader.tabs()).isZero();
        assertThat(backups.findLastFinished()).isPresent();
        assertThat(lastProblem.current()).isEmpty();
    }

    @Test
    void pauses_between_two_listings() {
        anInventoryOf(LongStream.rangeClosed(1, 50).toArray());
        reader.showing(LongStream.rangeClosed(1, 50).mapToObj(id -> aContent(id)).toArray(ListingContent[]::new));

        backUpWardrobe(Runnable::run, new BackupPace(new PauseRange(MINIMUM_PAUSE, MAXIMUM_PAUSE), new Random(42)))
                .backUp();

        assertThat(reader.pausesBetweenListings()).hasSize(49)
                .allSatisfy(pause -> assertThat(pause).isBetween(MINIMUM_PAUSE, MAXIMUM_PAUSE));
    }

    @Test
    void reason_of_an_unreadable_listing_goes_to_the_logs(CapturedOutput logs) {
        anInventoryOf(1);
        reader.whereFormIsUnreadable(1, "no condition on 1");

        backUp();

        assertThat(logs).contains("no condition on 1");
    }

    @Test
    void new_backup_clears_the_problem_of_the_previous_one() {
        reader.whereBrowserCannotStart();
        anInventoryOf(1);
        backUp();
        reader.afresh().showing(aContent(1));

        backUp();

        assertThat(lastProblem.current()).isEmpty();
    }

    @Test
    void unexpected_failure_ends_the_action_and_reaches_the_executor_for_the_logs() {
        anInventoryOf(1);
        reader.whereBrowserFailsUnexpectedly();

        Throwable failure = catchThrowable(this::backUp);

        assertThat(failure).isInstanceOf(IllegalStateException.class);
        assertThat(lastProblem.current()).contains(BackupProblem.BACKUP_FAILED);
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void unavailable_database_fails_the_backup_ends_the_action_and_leaves_it_to_resume() {
        anInventoryOf(1);
        listingBackups.whereDatabaseIsDown();
        reader.showing(aContent(1));

        catchThrowable(this::backUp);

        assertThat(lastProblem.current()).contains(BackupProblem.BACKUP_FAILED);
        assertThat(session.overview().currentAction()).isEmpty();
        assertThat(backups.findUnfinished()).isPresent();
    }

    @Test
    void photos_are_written_before_the_backup_that_points_at_them() {
        anInventoryOf(1);
        reader.showing(new ListingReading(aContent(1), List.of(aPhoto("A"), aPhoto("B"))));
        List<Set<String>> filesWhenSaving = new ArrayList<>();
        listingBackups.whenSaving(backup -> filesWhenSaving.add(photos.files(1).keySet()));

        backUp();

        assertThat(filesWhenSaving).containsExactly(Set.of(aPhoto("A").fileName(), aPhoto("B").fileName()));
    }

    @Test
    void previous_photos_are_removed_only_once_the_new_backup_is_saved() {
        anInventoryOf(1);
        reader.showing(new ListingReading(aContent(1), List.of(aPhoto("A"))));
        backUp();
        clock.advance(Duration.ofHours(1));
        reader.afresh().showing(new ListingReading(aContent(1), List.of(aPhoto("B"))));
        listingBackups.whereDatabaseIsDown();

        catchThrowable(this::backUp);

        assertThat(photos.files(1)).containsKey(aPhoto("A").fileName());
        assertThat(listingBackups.find(1).orElseThrow().photos()).containsExactly(ListingPhoto.at(0, aPhoto("A")));
    }

    @Test
    void same_photo_twice_in_a_listing_is_one_file_kept_at_both_ranks() {
        anInventoryOf(1);
        reader.showing(new ListingReading(aContent(1), List.of(aPhoto("A"), aPhoto("A"))));

        backUp();

        assertThat(photos.files(1)).containsOnlyKeys(aPhoto("A").fileName());
        assertThat(listingBackups.find(1).orElseThrow().photos())
                .containsExactly(ListingPhoto.at(0, aPhoto("A")), ListingPhoto.at(1, aPhoto("A")));
    }

    @Test
    void failed_write_fails_the_backup_and_saves_nothing() {
        anInventoryOf(1);
        reader.showing(aContent(1));
        photos.whereWritingFails();

        Throwable failure = catchThrowable(this::backUp);

        assertThat(failure).isInstanceOf(UncheckedIOException.class);
        assertThat(lastProblem.current()).contains(BackupProblem.BACKUP_FAILED);
        assertThat(listingBackups.findAll()).isEmpty();
        assertThat(backups.findUnfinished()).isPresent();
    }

    private void anInventoryOf(long... ids) {
        listings.saveAll(LongStream.of(ids).mapToObj(id -> Listing.seen(anActiveListing(id), EARLIER)).toList());
    }

    private void backUp() {
        backUpWardrobe(Runnable::run, noBackupPause()).backUp();
    }

    private BackUpWardrobe backUpWardrobe(Executor executor, BackupPace pace) {
        return new BackUpWardrobe(session, lastProblem, backups, reader, listings, listingBackups, photos, pace, clock,
                executor);
    }
}
