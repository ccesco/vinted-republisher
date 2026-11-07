package fr.vinted.republisher.application.wardrobe;

import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.SessionOverview;
import fr.vinted.republisher.domain.session.SessionStatus;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.LastScanProblem;
import fr.vinted.republisher.domain.wardrobe.Listing;
import fr.vinted.republisher.domain.wardrobe.ScanProblem;
import fr.vinted.republisher.domain.wardrobe.VintedListingId;
import fr.vinted.republisher.domain.wardrobe.WardrobeScan;
import fr.vinted.republisher.fakes.AdjustableClock;
import fr.vinted.republisher.fakes.FakeWardrobeReader;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import fr.vinted.republisher.fakes.InMemoryWardrobeScanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anInactiveListing;
import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.noPause;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@ExtendWith(OutputCaptureExtension.class)
class ScanWardrobeTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(3600);
    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private final VintedSession session = new VintedSession();
    private final LastScanProblem lastProblem = new LastScanProblem();
    private final InMemoryWardrobeScanRepository scans = new InMemoryWardrobeScanRepository();
    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final FakeWardrobeReader reader = new FakeWardrobeReader();
    private final AdjustableClock clock = new AdjustableClock(NOW);
    private final List<Runnable> background = new ArrayList<>();

    @Test
    void shows_the_scan_in_progress_then_runs_it_in_the_background() {
        reader.holding(anActiveListing(1));

        scanWardrobe(background::add).scan();

        assertThat(session.overview().currentAction()).contains(BrowserAction.SCANNING);
        assertThat(reader.reads()).isZero();

        background.forEach(Runnable::run);

        assertThat(session.overview().currentAction()).isEmpty();
        assertThat(listings.findAll()).hasSize(1);
    }

    @Test
    void does_not_change_what_is_known_of_the_session() {
        session.start(BrowserAction.CHECKING);
        session.complete(SessionStatus.CONNECTED);
        reader.whereVintedRefusesTheWardrobe();

        scan();

        assertThat(session.overview()).isEqualTo(SessionOverview.idle(SessionStatus.CONNECTED));
    }

    @Test
    void each_listing_is_stamped_with_the_moment_it_was_read() {
        clock.advancingAtEachReadingBy(Duration.ofSeconds(10));
        reader.holding(anActiveListing(1), anActiveListing(2));

        scan();

        assertThat(listings.findAll()).extracting(Listing::lastSeenAt)
                .containsExactlyInAnyOrder(NOW.plusSeconds(10), NOW.plusSeconds(20));
    }

    @Test
    void scan_ends_after_its_last_reading_and_is_dated_by_that_end() {
        clock.advancingAtEachReadingBy(Duration.ofSeconds(10));
        reader.holding(anActiveListing(1));

        scan();

        WardrobeScan scan = scans.findLastFinished().orElseThrow();
        assertThat(scan.startedAt()).isEqualTo(NOW);
        assertThat(listings.findAll().getFirst().lastSeenAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(scan.finishedAt()).contains(NOW.plusSeconds(20));
    }

    @Test
    void failed_scan_stays_unfinished() {
        reader.holding(anActiveListing(1)).whereVintedRefuses(1);

        scan();

        assertThat(scans.findUnfinished()).isPresent();
        assertThat(scans.findLastFinished()).isEmpty();
    }

    @Test
    void resumed_scan_keeps_its_start() {
        reader.whereBrowserCannotStart();
        scan();
        clock.advance(ONE_HOUR);
        reader.holding(anActiveListing(1));

        scan();

        assertThat(scans.findUnfinished()).isEmpty();
        assertThat(scans.findLastFinished().orElseThrow().startedAt()).isEqualTo(NOW);
    }

    @Test
    void scan_after_a_finished_one_opens_every_listing_again() {
        reader.holding(anActiveListing(1));
        scan();
        clock.advance(ONE_HOUR);
        reader.holding(anActiveListing(1));

        scan();

        assertThat(reader.opened()).containsExactly(new VintedListingId(1));
        assertThat(scans.findLastFinished().orElseThrow().startedAt()).isEqualTo(NOW.plus(ONE_HOUR));
    }

    @Test
    void inactive_listing_is_opened_again_when_a_scan_resumes() {
        reader.holding(anActiveListing(1), anInactiveListing(2), anActiveListing(3)).whereVintedRefuses(3);
        scan();
        reader.holding(anActiveListing(1), anInactiveListing(2), anActiveListing(3));

        scan();

        assertThat(reader.opened()).containsExactly(new VintedListingId(2), new VintedListingId(3));
    }

    @Test
    void known_listing_no_longer_active_ends_up_absent() {
        Listing known = Listing.seen(anActiveListing(1), EARLIER);
        listings.saveAll(List.of(known));
        reader.holding(anInactiveListing(1));

        scan();

        assertThat(listings.findAll()).containsExactly(known.absent());
    }

    @Test
    void absent_listing_found_again_is_present_again() {
        listings.saveAll(List.of(Listing.seen(anActiveListing(1), EARLIER).absent()));
        reader.holding(anActiveListing(1));

        scan();

        assertThat(listings.findAll()).containsExactly(Listing.seen(anActiveListing(1), NOW));
    }

    @Test
    void reason_of_an_incomplete_listing_of_the_wardrobe_goes_to_the_logs(CapturedOutput logs) {
        reader.announcing(3, anActiveListing(1));

        scan();

        assertThat(logs).contains("1 links found, 3 announced");
    }

    @Test
    void reason_of_an_unreachable_vinted_goes_to_the_logs(CapturedOutput logs) {
        reader.whereVintedRefusesTheWardrobe();

        scan();

        assertThat(logs).contains("HTTP 403");
    }

    @Test
    void new_scan_clears_the_problem_of_the_previous_one() {
        reader.whereVintedRefusesTheWardrobe();
        scan();
        reader.holding(anActiveListing(1));

        scan();

        assertThat(lastProblem.current()).isEmpty();
    }

    @Test
    void unexpected_failure_ends_the_action_and_reaches_the_executor_for_the_logs() {
        reader.whereBrowserFailsUnexpectedly();

        Throwable failure = catchThrowable(this::scan);

        assertThat(failure).isInstanceOf(IllegalStateException.class);
        assertThat(lastProblem.current()).contains(ScanProblem.SCAN_FAILED);
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void unavailable_database_fails_the_scan_ends_the_action_and_leaves_it_to_resume() {
        listings.whereDatabaseIsDown();
        reader.holding(anActiveListing(1));

        catchThrowable(this::scan);

        assertThat(lastProblem.current()).contains(ScanProblem.SCAN_FAILED);
        assertThat(session.overview().currentAction()).isEmpty();
        assertThat(scans.findUnfinished()).isPresent();
    }

    private void scan() {
        scanWardrobe(Runnable::run).scan();
    }

    private ScanWardrobe scanWardrobe(Executor executor) {
        return new ScanWardrobe(session, lastProblem, scans, reader, listings, noPause(), clock, executor);
    }
}
