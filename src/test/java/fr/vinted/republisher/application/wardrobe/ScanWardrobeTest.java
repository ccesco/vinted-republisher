package fr.vinted.republisher.application.wardrobe;

import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.SessionOverview;
import fr.vinted.republisher.domain.session.SessionStatus;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.ScanPace;
import fr.vinted.republisher.domain.wardrobe.ScanProblem;
import fr.vinted.republisher.domain.wardrobe.WardrobeScan;
import fr.vinted.republisher.fakes.FakeWardrobeReader;
import fr.vinted.republisher.fakes.InMemoryListingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executor;

import static fr.vinted.republisher.domain.wardrobe.WardrobeFixtures.anActiveListing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@ExtendWith(OutputCaptureExtension.class)
class ScanWardrobeTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    private final VintedSession session = new VintedSession();
    private final WardrobeScan wardrobeScan = new WardrobeScan();
    private final InMemoryListingRepository listings = new InMemoryListingRepository();
    private final FakeWardrobeReader reader = new FakeWardrobeReader();
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
        reader.whereVintedRefuses();

        scanWardrobe(Runnable::run).scan();

        assertThat(session.overview()).isEqualTo(SessionOverview.idle(SessionStatus.CONNECTED));
    }

    @Test
    void reason_of_an_incomplete_scan_goes_to_the_logs(CapturedOutput logs) {
        reader.announcing(3, anActiveListing(1));

        scanWardrobe(Runnable::run).scan();

        assertThat(logs).contains("1 listings read, 3 announced");
    }

    @Test
    void reason_of_an_unreachable_vinted_goes_to_the_logs(CapturedOutput logs) {
        reader.whereVintedRefuses();

        scanWardrobe(Runnable::run).scan();

        assertThat(logs).contains("HTTP 403");
    }

    @Test
    void new_scan_clears_the_problem_of_the_previous_one() {
        reader.whereVintedRefuses();
        scanWardrobe(Runnable::run).scan();
        reader.holding(anActiveListing(1));

        scanWardrobe(Runnable::run).scan();

        assertThat(wardrobeScan.problem()).isEmpty();
    }

    @Test
    void unexpected_failure_ends_the_action_and_reaches_the_executor_for_the_logs() {
        reader.whereBrowserFailsUnexpectedly();

        Throwable failure = catchThrowable(() -> scanWardrobe(Runnable::run).scan());

        assertThat(failure).isInstanceOf(IllegalStateException.class);
        assertThat(wardrobeScan.problem()).contains(ScanProblem.SCAN_FAILED);
        assertThat(session.overview().currentAction()).isEmpty();
    }

    @Test
    void unavailable_database_fails_the_scan_and_ends_the_action() {
        listings.whereDatabaseIsDown();
        reader.holding(anActiveListing(1));

        catchThrowable(() -> scanWardrobe(Runnable::run).scan());

        assertThat(wardrobeScan.problem()).contains(ScanProblem.SCAN_FAILED);
        assertThat(session.overview().currentAction()).isEmpty();
    }

    private ScanWardrobe scanWardrobe(Executor executor) {
        return new ScanWardrobe(session, wardrobeScan, reader, listings,
                new ScanPace(Duration.ZERO, Duration.ZERO, new Random(42)), Clock.fixed(NOW, ZoneOffset.UTC), executor);
    }
}
