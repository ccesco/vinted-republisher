package fr.vinted.republisher.application.wardrobe;

import fr.vinted.republisher.domain.session.BrowserAction;
import fr.vinted.republisher.domain.session.VintedSession;
import fr.vinted.republisher.domain.wardrobe.LastScanProblem;
import fr.vinted.republisher.domain.wardrobe.ScanProblem;
import fr.vinted.republisher.domain.wardrobe.WardrobeOverview;
import fr.vinted.republisher.domain.wardrobe.WardrobeScan;
import fr.vinted.republisher.fakes.InMemoryWardrobeScanRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ShowWardrobeTest {

    private static final Instant STARTED_AT = Instant.parse("2026-11-06T09:30:00Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-11-06T10:00:00Z");

    private final VintedSession session = new VintedSession();
    private final LastScanProblem lastProblem = new LastScanProblem();
    private final InMemoryWardrobeScanRepository scans = new InMemoryWardrobeScanRepository();
    private final ShowWardrobe showWardrobe = new ShowWardrobe(session, lastProblem, scans);

    @Test
    void nothing_is_known_before_the_first_scan() {
        assertThat(showWardrobe.show()).isEqualTo(new WardrobeOverview(false, Optional.empty(), Optional.empty()));
    }

    @Test
    void date_of_the_last_scan_is_the_end_of_the_last_finished_one() {
        scans.save(WardrobeScan.startedAt(STARTED_AT).finished(FINISHED_AT));

        assertThat(showWardrobe.show().lastScanAt()).contains(FINISHED_AT);
    }

    @Test
    void unfinished_scan_gives_no_date() {
        scans.save(WardrobeScan.startedAt(STARTED_AT));

        assertThat(showWardrobe.show().lastScanAt()).isEmpty();
    }

    @Test
    void tells_a_scan_is_in_progress() {
        session.start(BrowserAction.SCANNING);

        assertThat(showWardrobe.show().scanInProgress()).isTrue();
    }

    @Test
    void another_browser_action_is_not_a_scan_in_progress() {
        session.start(BrowserAction.CHECKING);

        assertThat(showWardrobe.show().scanInProgress()).isFalse();
    }

    @Test
    void shows_the_problem_of_the_last_scan() {
        lastProblem.report(ScanProblem.WARDROBE_UNREADABLE);

        assertThat(showWardrobe.show().problem()).contains(ScanProblem.WARDROBE_UNREADABLE);
    }
}
